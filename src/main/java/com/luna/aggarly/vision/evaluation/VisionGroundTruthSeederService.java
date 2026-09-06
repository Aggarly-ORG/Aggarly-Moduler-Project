package com.luna.aggarly.vision.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.property.entity.Address;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.PropertyImage;
import com.luna.aggarly.property.entity.enums.CancellationPolicy;
import com.luna.aggarly.property.entity.enums.PropertyStatus;
import com.luna.aggarly.property.entity.enums.PropertyType;
import com.luna.aggarly.property.repository.PropertyImageRepository;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.UserRepository;
import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.entity.VisionEvalQuery;
import com.luna.aggarly.vision.entity.enums.ImageQualityGrade;
import com.luna.aggarly.vision.entity.enums.ModerationStatus;
import com.luna.aggarly.vision.entity.enums.SceneType;
import com.luna.aggarly.vision.entity.enums.ViewType;
import com.luna.aggarly.vision.entity.enums.VisionIndexState;
import com.luna.aggarly.vision.entity.enums.VisionProcessingStatus;
import com.luna.aggarly.vision.pipeline.PerceptualHashService;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import com.luna.aggarly.vision.repository.VisionEvalQueryRepository;
import com.luna.aggarly.vision.vector.MultimodalEmbeddingService;
import com.luna.aggarly.vision.vector.PropertyDescriptionEmbeddingService;
import com.luna.aggarly.vision.vector.PropertyVisualProfileAggregator;
import com.luna.aggarly.vision.vector.QdrantVisionClient;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionGroundTruthSeederService {

    private final PropertyRepository propertyRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final PropertyImageAiMetadataRepository metadataRepository;
    private final VisionEvalQueryRepository evalQueryRepository;
    private final UserRepository userRepository;
    private final MultimodalEmbeddingService embeddingService;
    private final PerceptualHashService perceptualHashService;
    private final QdrantVisionClient qdrantClient;
    private final PropertyDescriptionEmbeddingService descriptionEmbeddingService;
    private final PropertyVisualProfileAggregator profileAggregator;
    private final ObjectMapper objectMapper;

    @Value("${aggarly.qdrant.collections.images:property_images_v1}")
    private String imagesCollection = "property_images_v1";

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.minio.MinioClient minioClient;

    @Value("${app.storage.bucket-name:aggarly}")
    private String minioBucket = "aggarly";

    private static final List<String> PROPERTY_SLUGS = List.of(
            "01_santorini_villa",
            "02_zermatt_chalet",
            "03_manhattan_penthouse",
            "04_bali_bambu_sanctuary",
            "05_copenhagen_penthouse",
            "06_marrakech_riad",
            "07_ibiza_sunset_villa",
            "08_kyoto_machiya",
            "09_scottish_castle",
            "10_tuscany_farmhouse"
    );

    public record SeedingSummary(
            int propertiesCreated,
            int imagesCreated,
            int queriesCreated,
            List<String> propertyTitles
    ) {}

    @Transactional
    public SeedingSummary seedGroundTruthDataset() {
        log.info("Starting Ground Truth Database Seeding (10 Properties, 110 Photos, 30 Queries)");

        // 0. Clean up previous evaluation queries to prevent stale expected-ID mismatches
        evalQueryRepository.deleteAll();

        // 1. Ensure test host user
        User hostUser = getOrCreateTestHost();

        // 2. Define 10 Properties with 11 Photos each (total 110 photos)
        List<PropertySeedData> propertyDataList = buildPropertyDataset();
        List<Property> createdProperties = new ArrayList<>();
        int totalImages = 0;

        for (int pIdx = 0; pIdx < propertyDataList.size(); pIdx++) {
            PropertySeedData pData = propertyDataList.get(pIdx);
            String slug = pIdx < PROPERTY_SLUGS.size() ? PROPERTY_SLUGS.get(pIdx) : "";
            Property property = Property.builder()
                    .hostId(hostUser.getId())
                    .title(pData.title())
                    .description(pData.description())
                    .propertyType(pData.propertyType())
                    .maxGuests(pData.maxGuests())
                    .bedrooms(pData.bedrooms())
                    .bathrooms(pData.bathrooms())
                    .basePricePerNight(BigDecimal.valueOf(pData.basePrice()))
                    .cancellationPolicy(CancellationPolicy.FLEXIBLE)
                    .latitude(BigDecimal.valueOf(pData.latitude()))
                    .longitude(BigDecimal.valueOf(pData.longitude()))
                    .status(PropertyStatus.ACTIVE)
                    .avgRating(BigDecimal.valueOf(4.92))
                    .reviewCount(18)
                    .build();

            Address address = Address.builder()
                    .street(pData.street())
                    .city(pData.city())
                    .state(pData.state())
                    .country(pData.country())
                    .zipCode(pData.zipCode())
                    .build();
            property.setAddress(address);

            Property savedProperty = propertyRepository.save(property);
            createdProperties.add(savedProperty);

            // Seed 11 photos for this property
            int order = 1;
            for (PhotoSeedData photo : pData.photos()) {
                String objectKey = "properties/gt-" + savedProperty.getId().toString().substring(0, 8) + "-" + order + ".jpg";
                PropertyImage pImage = PropertyImage.builder()
                        .property(savedProperty)
                        .objectKey(objectKey)
                        .displayOrder(order)
                        .isCover(photo.isCover())
                        .widthPx(640)
                        .heightPx(480)
                        .contentType("image/jpeg")
                        .build();
                PropertyImage savedImage = propertyImageRepository.save(pImage);

                // Load real JPEG image bytes from disk (data/ground-truth-photos/) or generate fallback
                byte[] imageBytes = loadOrGenerateImageBytes(slug, order, pData, photo);

                // Upload to MinIO storage if available
                if (minioClient != null) {
                    try {
                        boolean bucketExists = minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(minioBucket).build());
                        if (!bucketExists) {
                            minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(minioBucket).build());
                        }
                        minioClient.putObject(
                                io.minio.PutObjectArgs.builder()
                                        .bucket(minioBucket)
                                        .object(objectKey)
                                        .stream(new java.io.ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                                        .contentType("image/jpeg")
                                        .build()
                        );
                    } catch (Exception ex) {
                        log.debug("MinIO upload skipped for {}: {}", objectKey, ex.getMessage());
                    }
                }

                // Compute real perceptual hash (dHash) from image bytes
                String realPhash = perceptualHashService.computeDHash(imageBytes);
                if (realPhash == null || realPhash.isBlank()) {
                    realPhash = "phash-" + savedImage.getId();
                }

                // Embed multi-vector (image_vector + caption_vector) with model-aware cache
                float[] imageVec = embeddingService.embedImage(imageBytes, realPhash);
                float[] captionVec = embeddingService.embedCaption(photo.caption(), photo.altText(), photo.visualSummary());

                UUID pointId = UUID.randomUUID();

                // Save PropertyImageAiMetadata
                PropertyImageAiMetadata meta = PropertyImageAiMetadata.builder()
                        .propertyImage(savedImage)
                        .property(savedProperty)
                        .pipelineVersion("1.0.0")
                        .processingStatus(VisionProcessingStatus.COMPLETED)
                        .vectorIndexState(VisionIndexState.INDEXED)
                        .qdrantPointId(pointId)
                        .qdrantCollection(imagesCollection)
                        .perceptualHash(realPhash)
                        .qualityScore(photo.qualityScore())
                        .qualityGrade(photo.qualityGrade())
                        .sceneType(photo.sceneType())
                        .sceneConfidence(0.96)
                        .viewType(photo.viewType())
                        .roomClusterId(photo.roomClusterId())
                        .aiCaption(photo.caption())
                        .altText(photo.altText())
                        .visualSummary(photo.visualSummary())
                        .detectedAmenitiesJson(toJson(photo.detectedAmenities()))
                        .styleTagsJson(toJson(photo.styleTags()))
                        .moderationStatus(ModerationStatus.APPROVED)
                        .lastProcessedAt(Instant.now())
                        .embeddedAt(Instant.now())
                        .build();
                metadataRepository.save(meta);

                // Upsert point to Qdrant collection property_images_v1
                Map<String, float[]> namedVectors = Map.of(
                        "image_vector", imageVec,
                        "caption_vector", captionVec
                );
                Map<String, Object> payload = new HashMap<>();
                payload.put("propertyId", savedProperty.getId().toString());
                payload.put("imageId", savedImage.getId().toString());
                payload.put("objectKey", objectKey);
                payload.put("sceneType", photo.sceneType().name());
                payload.put("qualityScore", photo.qualityScore());
                payload.put("qualityGrade", photo.qualityGrade().name());
                payload.put("city", pData.city().trim().toLowerCase());
                payload.put("country", pData.country().trim().toLowerCase());
                payload.put("pricePerNight", pData.basePrice());
                payload.put("maxGuests", pData.maxGuests());
                payload.put("moderationStatus", "APPROVED");

                qdrantClient.upsertMultiVectorPoint(imagesCollection, pointId, namedVectors, payload);
                totalImages++;
                order++;
            }

            // Generate description vector & centroid profile for property
            try {
                descriptionEmbeddingService.embedAndUpsertProperty(savedProperty.getId());
                profileAggregator.aggregatePropertyProfile(savedProperty.getId());
            } catch (Exception ex) {
                log.warn("Error aggregating visual profile for property {}: {}", savedProperty.getId(), ex.getMessage());
            }
        }

        // 3. Create 30 Ground Truth Queries
        List<VisionEvalQuery> evalQueries = buildEvaluationQueries(createdProperties);
        evalQueryRepository.saveAll(evalQueries);

        log.info("Ground Truth Database successfully seeded: {} properties, {} photos, {} eval queries",
                createdProperties.size(), totalImages, evalQueries.size());

        return new SeedingSummary(
                createdProperties.size(),
                totalImages,
                evalQueries.size(),
                createdProperties.stream().map(Property::getTitle).toList()
        );
    }

    private User getOrCreateTestHost() {
        try {
            UUID currentUserId = com.luna.aggarly.common.security.SecurityUtils.getCurrentUserId();
            if (currentUserId != null) {
                var current = userRepository.findById(currentUserId);
                if (current.isPresent()) {
                    return current.get();
                }
            }
        } catch (Exception ignored) {}

        return userRepository.findByEmail("test-host@aggarly.com")
                .orElseGet(() -> {
                    User host = User.builder()
                            .email("test-host@aggarly.com")
                            .username("testhost")
                            .displayName("Aggarly Verified Host")
                            .firstName("Verified")
                            .lastName("Host")
                            .emailVerified(true)
                            .build();
                    return userRepository.save(host);
                });
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private byte[] loadOrGenerateImageBytes(String propertySlug, int order, PropertySeedData pData, PhotoSeedData photo) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("data", "ground-truth-photos", propertySlug);
            if (java.nio.file.Files.exists(dir)) {
                String prefix = String.format("%02d_", order);
                try (var stream = java.nio.file.Files.list(dir)) {
                    var match = stream.filter(p -> p.getFileName().toString().startsWith(prefix) && p.getFileName().toString().endsWith(".jpg")).findFirst();
                    if (match.isPresent()) {
                        byte[] diskBytes = java.nio.file.Files.readAllBytes(match.get());
                        if (diskBytes.length > 5000) {
                            return diskBytes;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not load photo from disk for {} (order {}): {}", propertySlug, order, e.getMessage());
        }
        return generateSceneImageBytes(pData.title(), photo.roomName(), photo.bgRgb(), photo.textRgb());
    }

    private byte[] generateSceneImageBytes(String title, String sceneName, int bgRgb, int textRgb) {
        try {
            int width = 640, height = 480;
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bgColor = new Color(bgRgb);
            g.setColor(bgColor);
            g.fillRect(0, 0, width, height);

            // Shaded gradient for visual depth
            GradientPaint gp = new GradientPaint(0, 0, bgColor, width, height, bgColor.darker());
            g.setPaint(gp);
            g.fillRect(0, 0, width, height);

            // Text labels
            g.setColor(new Color(textRgb));
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.drawString(title.length() > 40 ? title.substring(0, 37) + "..." : title, 30, 60);

            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g.drawString("Scene: " + sceneName, 30, 100);

            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    // Records for defining seed data cleanly
    public record PropertySeedData(
            String title,
            String description,
            PropertyType propertyType,
            int maxGuests,
            int bedrooms,
            int bathrooms,
            double basePrice,
            double latitude,
            double longitude,
            String street,
            String city,
            String state,
            String country,
            String zipCode,
            List<PhotoSeedData> photos
    ) {}

    public record PhotoSeedData(
            String roomName,
            SceneType sceneType,
            ViewType viewType,
            String roomClusterId,
            boolean isCover,
            double qualityScore,
            ImageQualityGrade qualityGrade,
            String caption,
            String altText,
            String visualSummary,
            List<String> detectedAmenities,
            List<String> styleTags,
            int bgRgb,
            int textRgb
    ) {}

    private List<PropertySeedData> buildPropertyDataset() {
        List<PropertySeedData> list = new ArrayList<>();

        // 1. Santorini Cliffside Villa
        list.add(new PropertySeedData(
                "Villa Azure Santorini — Cliffside Luxury Cave Villa with Heated Infinity Pool",
                "Spectacular whitewashed luxury cave villa perched on the volcanic cliffs of Oia overlooking the caldera and Aegean Sea. Features a private heated infinity pool extending over the cliff, expansive sunset terrace with sun loungers, master cave bedroom with king bed, luxury en-suite bathroom with rain shower and freestanding soaking tub, open-plan living area, and panoramic Aegean sea views.",
                PropertyType.VILLA, 6, 3, 3, 650.0, 36.4618, 25.3753,
                "Oia Caldera Cliffway", "Santorini", "Cyclades", "Greece", "84702",
                List.of(
                        new PhotoSeedData("Whitewashed Cliffside Facade", SceneType.EXTERIOR, ViewType.SEA_VIEW, "EXTERIOR", false, 0.96, ImageQualityGrade.EXCELLENT, "Whitewashed cycladic cave architecture on the volcanic cliffs of Oia", "Whitewashed cliffside building overlooking Aegean Sea", "Luxury cycladic architecture", List.of("cliffside", "caldera_view"), List.of("cycladic", "luxury"), 0x0077b6, 0xffffff),
                        new PhotoSeedData("Cycladic Arched Living Room", SceneType.LIVING_ROOM, ViewType.SEA_VIEW, "LIVING_ROOM", false, 0.94, ImageQualityGrade.EXCELLENT, "Minimalist cycladic curved arch lounge overlooking the Aegean Sea", "Living room with arched doorway and ocean panorama", "Sunlit whitewashed lounge", List.of("sea_view", "arched_windows"), List.of("cycladic", "minimalist"), 0x0096c7, 0xffffff),
                        new PhotoSeedData("Caldera View Dining Area", SceneType.DINING, ViewType.SEA_VIEW, "DINING", false, 0.91, ImageQualityGrade.GOOD, "Caldera-view indoor dining table set for 6 guests", "Dining table with panoramic sea view", "Scenic dining space", List.of("sea_view"), List.of("cycladic"), 0x00b4d8, 0xffffff),
                        new PhotoSeedData("Minimalist Island Kitchen", SceneType.KITCHEN, ViewType.NONE, "KITCHEN", false, 0.90, ImageQualityGrade.GOOD, "Modern white minimalist island kitchen with built-in appliances", "White kitchen with central marble island", "Sleek modern kitchen", List.of("island", "espresso_machine"), List.of("modern", "minimalist"), 0x48cae4, 0x000000),
                        new PhotoSeedData("Master Cave Suite", SceneType.BEDROOM, ViewType.SEA_VIEW, "BEDROOM_1", false, 0.97, ImageQualityGrade.EXCELLENT, "Arched cave master bedroom with king bed and private sea view balcony", "Master cave bedroom overlooking caldera", "Romantic luxury bedroom", List.of("king_bed", "sea_view", "balcony"), List.of("cycladic", "cave", "luxury"), 0x023e8a, 0xffffff),
                        new PhotoSeedData("Cycladic Double Guest Room", SceneType.BEDROOM, ViewType.NONE, "BEDROOM_2", false, 0.89, ImageQualityGrade.GOOD, "Cozy whitewashed double guest bedroom with natural light", "Double guest bedroom with built-in plaster shelves", "Cozy cycladic bedroom", List.of("queen_bed"), List.of("cycladic"), 0x03045e, 0xffffff),
                        new PhotoSeedData("Twin Sea Breeze Bedroom", SceneType.BEDROOM, ViewType.SEA_VIEW, "BEDROOM_3", false, 0.88, ImageQualityGrade.GOOD, "Twin guest bedroom opening to side terrace with sea breeze", "Twin bedroom with arched windows", "Breezy coastal bedroom", List.of("twin_beds", "sea_view"), List.of("cycladic"), 0x0077b6, 0xffffff),
                        new PhotoSeedData("Caldera Marble Soaking Bathroom", SceneType.BATHROOM, ViewType.SEA_VIEW, "BATHROOM_1", false, 0.95, ImageQualityGrade.EXCELLENT, "Marble freestanding bathtub situated in front of caldera sea view window", "Luxury bathtub overlooking Aegean horizon", "Spa soaking bathroom", List.of("bathtub", "rain_shower", "sea_view"), List.of("luxury", "marble"), 0x0096c7, 0xffffff),
                        new PhotoSeedData("Tadelakt Rain Shower Bathroom", SceneType.BATHROOM, ViewType.NONE, "BATHROOM_2", false, 0.89, ImageQualityGrade.GOOD, "Smooth tadelakt walk-in rain shower bathroom with brass fittings", "Minimalist plaster bathroom with rain shower", "Modern plaster bath", List.of("rain_shower"), List.of("minimalist"), 0x48cae4, 0x000000),
                        new PhotoSeedData("Sunset Pergola Cocktail Terrace", SceneType.BALCONY, ViewType.SEA_VIEW, "BALCONY", false, 0.96, ImageQualityGrade.EXCELLENT, "Pergola-shaded sunset cocktail terrace with panoramic Aegean views", "Terrace with sunbeds facing golden hour sunset", "Scenic outdoor sunset terrace", List.of("sunset_view", "sunbeds", "terrace"), List.of("outdoor", "pergola"), 0xffb703, 0x000000),
                        new PhotoSeedData("Heated Cliffside Infinity Pool", SceneType.POOL, ViewType.SEA_VIEW, "POOL", true, 0.98, ImageQualityGrade.EXCELLENT, "Private heated infinity pool hanging over the Oia cliff edge into the caldera", "Infinity pool edge overlooking turquoise sea", "Signature luxury pool", List.of("infinity_pool", "private_pool", "heated_pool", "sea_view"), List.of("infinity_pool", "luxury"), 0x0077b6, 0xffffff)
                )
        ));

        // 2. Swiss Alpine Luxury Chalet
        list.add(new PropertySeedData(
                "Chalet Zermatt Peak — Luxury Alpine Timber Chalet with Matterhorn View & Sauna",
                "Authentic hand-hewn timber alpine chalet nestled in the Swiss Alps with floor-to-ceiling panoramic views of the Matterhorn. Features a roaring natural stone wood-burning fireplace, Finnish cedar sauna, outdoor heated jacuzzi surrounded by snow, gourmet Swiss pine kitchen, plush sheepskin-adorned living room, and heated ski storage.",
                PropertyType.CABIN, 8, 4, 4, 850.0, 45.9765, 7.7491,
                "Oberdorfstrasse 42", "Zermatt", "Valais", "Switzerland", "3920",
                List.of(
                        new PhotoSeedData("Snowy Timber Chalet Exterior", SceneType.EXTERIOR, ViewType.MOUNTAIN, "EXTERIOR", true, 0.95, ImageQualityGrade.EXCELLENT, "Snow-covered hand-hewn timber chalet facing the alpine peaks", "Swiss chalet facade covered in deep winter snow", "Authentic alpine exterior", List.of("snow", "mountain_view", "ski_in_ski_out"), List.of("alpine", "rustic", "timber"), 0x3d405b, 0xffffff),
                        new PhotoSeedData("Grand Fireplace Cathedral Lounge", SceneType.LIVING_ROOM, ViewType.MOUNTAIN, "LIVING_ROOM", false, 0.96, ImageQualityGrade.EXCELLENT, "Double-height cathedral timber ceiling with roaring stone wood fireplace", "Living room with roaring stone fireplace and mountain view", "Warm cozy chalet lounge", List.of("fireplace", "mountain_view", "high_ceilings"), List.of("alpine", "cozy"), 0x81171b, 0xffffff),
                        new PhotoSeedData("Rustic Solid Oak Dining Room", SceneType.DINING, ViewType.MOUNTAIN, "DINING", false, 0.92, ImageQualityGrade.GOOD, "Rustic solid oak 10-person dining table under antler chandelier", "Chalet dining table with alpine view", "Traditional mountain dining", List.of("large_dining_table"), List.of("rustic"), 0x544738, 0xffffff),
                        new PhotoSeedData("Gourmet Swiss Pine Kitchen", SceneType.KITCHEN, ViewType.NONE, "KITCHEN", false, 0.91, ImageQualityGrade.GOOD, "Gourmet Swiss pine kitchen with copper pots and granite breakfast bar", "Mountain kitchen with professional range", "Chef alpine kitchen", List.of("breakfast_bar", "chef_kitchen"), List.of("alpine", "gourmet"), 0x6f4e37, 0xffffff),
                        new PhotoSeedData("Master Alpine Timber Suite", SceneType.BEDROOM, ViewType.MOUNTAIN, "BEDROOM_1", false, 0.95, ImageQualityGrade.EXCELLENT, "Chalet king master bedroom with exposed timber beams and snowy balcony", "Master bedroom with wooden beams and mountain panorama", "Luxury alpine bedroom", List.of("king_bed", "balcony", "mountain_view"), List.of("alpine", "luxury"), 0x3a5a40, 0xffffff),
                        new PhotoSeedData("Cozy Faux-Fur Guest Bedroom", SceneType.BEDROOM, ViewType.MOUNTAIN, "BEDROOM_2", false, 0.90, ImageQualityGrade.GOOD, "Cozy alpine timber guest room with plush faux-fur throws and mountain view", "Wooden bedroom with fur blankets", "Warm winter bedroom", List.of("queen_bed", "mountain_view"), List.of("cozy"), 0x588157, 0xffffff),
                        new PhotoSeedData("Alpine Bunk Room for Skiers", SceneType.BEDROOM, ViewType.NONE, "BEDROOM_3", false, 0.88, ImageQualityGrade.GOOD, "Custom wooden bunk bedroom sleeping 4 ski enthusiasts", "Timber bunk beds for family or ski group", "Chalet bunk room", List.of("bunk_beds"), List.of("bunk"), 0xa3b18a, 0x000000),
                        new PhotoSeedData("Slate Stone Soaking Bathroom", SceneType.BATHROOM, ViewType.MOUNTAIN, "BATHROOM_1", false, 0.93, ImageQualityGrade.EXCELLENT, "Dark slate stone bathroom with deep freestanding tub overlooking mountains", "Slate bathroom with view of snow mountains", "Alpine spa bath", List.of("bathtub", "double_vanity"), List.of("slate", "luxury"), 0x2b2d42, 0xffffff),
                        new PhotoSeedData("Private Finnish Cedar Sauna", SceneType.OTHER, ViewType.NONE, "SAUNA", false, 0.94, ImageQualityGrade.EXCELLENT, "Private aromatic cedarwood Finnish sauna with heated volcanic stones", "Finnish sauna with wooden benches", "Wellness chalet sauna", List.of("sauna", "spa"), List.of("wellness", "spa"), 0xbc6c25, 0xffffff),
                        new PhotoSeedData("Matterhorn Viewing Deck", SceneType.BALCONY, ViewType.MOUNTAIN, "BALCONY", false, 0.97, ImageQualityGrade.EXCELLENT, "Timber outdoor balcony with panoramic view of the majestic Matterhorn", "Wooden deck looking toward snowy alpine summit", "Scenic mountain balcony", List.of("matterhorn_view", "sun_deck"), List.of("alpine"), 0x4a4e69, 0xffffff),
                        new PhotoSeedData("Steaming Outdoor Snow Jacuzzi", SceneType.POOL, ViewType.MOUNTAIN, "POOL", false, 0.98, ImageQualityGrade.EXCELLENT, "Steaming hot tub on the snowy terrace with mountain panorama", "Outdoor hot tub surrounded by deep snow", "Winter hot tub experience", List.of("jacuzzi", "hot_tub", "snow_view"), List.of("luxury", "wellness"), 0x1d3557, 0xffffff)
                )
        ));

        // 3. Manhattan Skyline Glass Penthouse
        list.add(new PropertySeedData(
                "The Obsidian Tower — Ultra-Modern Tribeca Duplex Penthouse with Private Skyline Terrace",
                "Sleek, ultra-modern luxury glass duplex penthouse soaring above Tribeca. Boasts 360-degree skyline views of the Empire State Building and Hudson River, Italian Poliform chef's kitchen, custom black marble fireplace, floor-to-ceiling acoustically insulated glass, executive dual-monitor Bloomberg workstation, and wrap-around private terrace.",
                PropertyType.LOFT, 4, 2, 3, 950.0, 40.7180, -74.0078,
                "185 Franklin Street, Duplex PH", "New York", "NY", "United States", "10013",
                List.of(
                        new PhotoSeedData("Architectural Glass Skyscraper Facade", SceneType.EXTERIOR, ViewType.CITY_SKYLINE, "EXTERIOR", false, 0.94, ImageQualityGrade.EXCELLENT, "Contemporary glass and black steel skyscraper soaring in Tribeca", "Glass skyscraper facade against city sky", "Modern architectural tower", List.of("skyscraper", "city_center"), List.of("modern", "architectural"), 0x14213d, 0xffffff),
                        new PhotoSeedData("Double-Height Skyline Living Room", SceneType.LIVING_ROOM, ViewType.CITY_SKYLINE, "LIVING_ROOM", true, 0.98, ImageQualityGrade.EXCELLENT, "Double-height soaring glass living room with glittering Manhattan night skyline", "Luxury living room with floor to ceiling glass city panorama", "Urban luxury penthouse lounge", List.of("skyline_view", "fireplace", "floor_to_ceiling_windows"), List.of("ultra_modern", "luxury"), 0x000814, 0xffffff),
                        new PhotoSeedData("Italian Designer Glass Dining", SceneType.DINING, ViewType.CITY_SKYLINE, "DINING", false, 0.92, ImageQualityGrade.GOOD, "Italian glass dining table with designer chandelier overlooking skyline", "Dining area with views of Manhattan towers", "Sleek dining room", List.of("city_view"), List.of("modern", "designer"), 0x001d3d, 0xffffff),
                        new PhotoSeedData("Calacatta Marble Waterfall Kitchen", SceneType.KITCHEN, ViewType.CITY_SKYLINE, "KITCHEN", false, 0.96, ImageQualityGrade.EXCELLENT, "Monolithic Calacatta marble waterfall island with integrated Miele appliances", "Luxury marble kitchen island overlooking city", "High-end chef kitchen", List.of("wine_fridge", "chef_kitchen", "island"), List.of("chef_kitchen", "marble"), 0xfca311, 0x000000),
                        new PhotoSeedData("Panoramic Corner Master Bedroom", SceneType.BEDROOM, ViewType.CITY_SKYLINE, "BEDROOM_1", false, 0.97, ImageQualityGrade.EXCELLENT, "Corner master suite enclosed by floor-to-ceiling illuminated city skyline", "Master bedroom with panoramic city view at twilight", "Sky-high master bedroom", List.of("king_bed", "skyline_view"), List.of("modern", "luxury"), 0x003566, 0xffffff),
                        new PhotoSeedData("Minimalist Skyline Guest Suite", SceneType.BEDROOM, ViewType.CITY_SKYLINE, "BEDROOM_2", false, 0.90, ImageQualityGrade.GOOD, "Modern minimalist guest bedroom with skyline views and en-suite bath", "Guest room with floor to ceiling windows", "Clean modern guest room", List.of("queen_bed", "city_view"), List.of("minimalist"), 0x22223b, 0xffffff),
                        new PhotoSeedData("Nero Marquina Black Marble Bath", SceneType.BATHROOM, ViewType.CITY_SKYLINE, "BATHROOM_1", false, 0.96, ImageQualityGrade.EXCELLENT, "Black Nero Marquina marble bathroom with freestanding soaking tub overlooking skyline", "Dark marble bath with city view bathtub", "Opulent marble bathroom", List.of("bathtub", "city_view", "walk_in_shower"), List.of("black_marble", "luxury"), 0x111111, 0xffffff),
                        new PhotoSeedData("Frameless Glass Guest Shower", SceneType.BATHROOM, ViewType.NONE, "BATHROOM_2", false, 0.89, ImageQualityGrade.GOOD, "Frameless glass rain shower with floating vanity and backlit mirror", "Modern bathroom with walk-in shower", "Minimalist powder room", List.of("rain_shower"), List.of("modern"), 0x4a4e69, 0xffffff),
                        new PhotoSeedData("Executive Dual-Display Corner Workstation", SceneType.WORKSPACE, ViewType.CITY_SKYLINE, "WORKSPACE", false, 0.97, ImageQualityGrade.EXCELLENT, "Executive glass corner office with ergonomic Aeron chair and dual 4K displays", "High-tech remote workstation overlooking Manhattan towers", "Productive nomad office", List.of("workspace", "dual_monitors", "ergonomic_chair", "fast_wifi"), List.of("executive", "workstation"), 0x2b2d42, 0xffffff),
                        new PhotoSeedData("Wrap-Around Fire Pit Skyline Terrace", SceneType.BALCONY, ViewType.CITY_SKYLINE, "BALCONY", false, 0.96, ImageQualityGrade.EXCELLENT, "Wrap-around penthouse rooftop terrace with modern linear gas fire pit lounge", "Rooftop lounge overlooking Empire State Building", "Penthouse terrace lounge", List.of("fire_pit", "skyline_terrace"), List.of("rooftop", "modern"), 0xd90429, 0xffffff),
                        new PhotoSeedData("Empire State & Hudson Twilight Panorama", SceneType.VIEW, ViewType.CITY_SKYLINE, "VIEW", false, 0.97, ImageQualityGrade.EXCELLENT, "Iconic panoramic twilight skyline view spanning Empire State to Hudson River", "Manhattan city skyline lights at dusk", "Breathtaking urban panorama", List.of("empire_state_view", "panoramic_view"), List.of("skyline", "panorama"), 0x03071e, 0xffffff)
                )
        ));

        // 4. Ubud Bamboo Jungle Eco-Sanctuary
        list.add(new PropertySeedData(
                "Bambu Indah Retreat — Curved Bamboo Architectural Sanctuary with Private Jungle Pool",
                "Eco-luxury architectural marvel hand-crafted entirely from curved sustainable bamboo in the tranquil Ayung River valley of Ubud. Features an open-air pavilion design immersed in tropical rainforest, private natural stone river pool, outdoor volcanic stone bathtub, organic garden, yoga shala deck, and hand-woven artisanal furnishings.",
                PropertyType.VILLA, 4, 2, 2, 320.0, -8.5069, 115.2625,
                "Jalan Raya Sayan, Ayung Valley", "Ubud", "Bali", "Indonesia", "80571",
                List.of(
                        new PhotoSeedData("Curved Bamboo Cathedral Exterior", SceneType.EXTERIOR, ViewType.GARDEN, "EXTERIOR", true, 0.97, ImageQualityGrade.EXCELLENT, "Curved multi-tier bamboo pavilion architecture rising above tropical palm jungle", "Organic bamboo villa nestled in lush Bali jungle", "Eco-architectural masterpiece", List.of("jungle_view", "bamboo_architecture"), List.of("bamboo", "eco_luxury", "tropical"), 0x2d6a4f, 0xffffff),
                        new PhotoSeedData("Open-Air Tropical Bamboo Lounge", SceneType.LIVING_ROOM, ViewType.GARDEN, "LIVING_ROOM", false, 0.94, ImageQualityGrade.EXCELLENT, "Open-air bamboo lounge with linen daybeds and tropical garden views", "Living room open to rainforest with bamboo furniture", "Relaxed tropical pavilion", List.of("jungle_view", "daybed"), List.of("open_air", "boho"), 0x40916c, 0xffffff),
                        new PhotoSeedData("Reclaimed Teak Communal Dining", SceneType.DINING, ViewType.GARDEN, "DINING", false, 0.90, ImageQualityGrade.GOOD, "Hand-carved reclaimed teak communal dining table overlooking organic garden", "Teak dining table in open pavilion", "Rustic tropical dining", List.of("organic_dining"), List.of("artisanal", "teak"), 0x74c69d, 0x000000),
                        new PhotoSeedData("Artisanal Open-Air Brass Kitchen", SceneType.KITCHEN, ViewType.GARDEN, "KITCHEN", false, 0.89, ImageQualityGrade.GOOD, "Artisanal open-air kitchen with hand-beaten brass fixtures and coffee maker", "Open bamboo kitchen with tropical breeze", "Organic food preparation area", List.of("coffee_maker"), List.of("open_air", "rustic"), 0x52b788, 0x000000),
                        new PhotoSeedData("Bamboo Canopy River Master Bedroom", SceneType.BEDROOM, ViewType.GARDEN, "BEDROOM_1", false, 0.96, ImageQualityGrade.EXCELLENT, "Bamboo canopy bedroom with organic mosquito net overlooking river rapids", "Bed with mosquito net in open bamboo room", "Romantic jungle bedroom", List.of("king_bed", "river_view", "canopy_bed"), List.of("canopy", "romantic"), 0x1b4332, 0xffffff),
                        new PhotoSeedData("Serene Bamboo Garden Guest Room", SceneType.BEDROOM, ViewType.GARDEN, "BEDROOM_2", false, 0.91, ImageQualityGrade.GOOD, "Serene garden-view bamboo bedroom with natural forest ventilation", "Bamboo guest room surrounded by palm fronds", "Peaceful eco bedroom", List.of("queen_bed", "jungle_view"), List.of("zen", "bamboo"), 0x081c15, 0xffffff),
                        new PhotoSeedData("Carved River Stone Outdoor Bath", SceneType.BATHROOM, ViewType.GARDEN, "BATHROOM_1", false, 0.96, ImageQualityGrade.EXCELLENT, "Open-air tropical garden bathroom with carved river stone soaking tub", "Outdoor stone bathtub surrounded by ferns and orchids", "Jungle spa bathroom", List.of("stone_bathtub", "outdoor_shower"), List.of("open_air_bath", "stone"), 0x2d6a4f, 0xffffff),
                        new PhotoSeedData("Bamboo Outdoor Rain Shower", SceneType.BATHROOM, ViewType.GARDEN, "BATHROOM_2", false, 0.92, ImageQualityGrade.GOOD, "Outdoor bamboo rain shower nestled under tall banana leaves", "Open-air garden shower in tropical greenery", "Refreshing nature shower", List.of("outdoor_shower"), List.of("outdoor", "tropical"), 0x40916c, 0xffffff),
                        new PhotoSeedData("Curved Ravine Bamboo Balcony", SceneType.BALCONY, ViewType.GARDEN, "BALCONY", false, 0.95, ImageQualityGrade.EXCELLENT, "Curved bamboo balcony hanging over the lush jungle ravine with woven hammock", "Bamboo treehouse balcony overlooking valley", "Suspended jungle terrace", List.of("hammock", "jungle_view"), List.of("bamboo", "treehouse"), 0x52b788, 0x000000),
                        new PhotoSeedData("Natural Stone River Jungle Pool", SceneType.POOL, ViewType.GARDEN, "POOL", false, 0.97, ImageQualityGrade.EXCELLENT, "Natural spring-fed stone swimming pool surrounded by tropical ferns and river stones", "Curved stone pool nestled in deep rainforest", "Private rainforest pool", List.of("private_pool", "jungle_pool"), List.of("natural_pool", "tropical"), 0x1b4332, 0xffffff),
                        new PhotoSeedData("Teak Yoga Shala & River Deck", SceneType.OTHER, ViewType.GARDEN, "YOGA_SHALA", false, 0.93, ImageQualityGrade.GOOD, "Teak meditation and yoga shala deck overlooking gentle river rapids", "Open wooden platform for yoga in nature", "Wellness jungle sanctuary", List.of("yoga_deck", "river_access"), List.of("wellness", "yoga"), 0x081c15, 0xffffff)
                )
        ));

        // 5. Copenhagen Scandinavian Minimalist Studio Loft
        list.add(new PropertySeedData(
                "Nordic Haven Loft — Sunlit Minimalist Scandinavian Studio with Dedicated Nomad Workspace",
                "A masterpiece of Danish Hygge and Scandinavian minimalism located in vibrant Nørrebro. Flooded with natural daylight through oversized arched industrial windows, featuring light oak herringbone floors, curated Arne Jacobsen designer furniture, state-of-the-art motorized sit-stand workspace with 4K display, specialty pour-over coffee bar, and bespoke birch plywood storage.",
                PropertyType.APARTMENT, 2, 1, 1, 180.0, 55.6867, 12.5562,
                "Jægersborggade 14, 3rd Floor", "Copenhagen", "Capital Region", "Denmark", "2200",
                List.of(
                        new PhotoSeedData("Historic Copenhagen Brick Facade", SceneType.EXTERIOR, ViewType.STREET, "EXTERIOR", false, 0.90, ImageQualityGrade.GOOD, "Historic Copenhagen red-brick building with classic arched entrance", "Brick residential facade in fashionable Danish neighborhood", "Classic Nordic street facade", List.of("city_center"), List.of("historic", "brick"), 0x6d597a, 0xffffff),
                        new PhotoSeedData("Light Oak Sun-Drenched Living Room", SceneType.LIVING_ROOM, ViewType.COURTYARD, "LIVING_ROOM", true, 0.96, ImageQualityGrade.EXCELLENT, "Sun-drenched minimalist living room with light oak herringbone floors and beige linen sofa", "Bright Scandinavian living room with natural light", "Airy minimalist Danish lounge", List.of("natural_light", "designer_furniture"), List.of("scandinavian", "minimalist", "hygge"), 0xe56b6f, 0xffffff),
                        new PhotoSeedData("Round Oak Dining with Wishbone Chairs", SceneType.DINING, ViewType.COURTYARD, "DINING", false, 0.92, ImageQualityGrade.GOOD, "Round oak dining table set with classic Hans Wegner Wishbone chairs", "Scandinavian dining nook with pendant light", "Iconic Danish dining", List.of("designer_chairs"), List.of("scandinavian", "oak"), 0xeaac8b, 0x000000),
                        new PhotoSeedData("Matte Black Reform Kitchen", SceneType.KITCHEN, ViewType.NONE, "KITCHEN", false, 0.93, ImageQualityGrade.EXCELLENT, "Matte black Reform architectural kitchen with stainless steel countertops", "Sleek black Danish kitchen with minimalist cabinets", "Designer culinary kitchen", List.of("dishwasher", "induction_cooktop"), List.of("modern_minimalist", "matte_black"), 0x355070, 0xffffff),
                        new PhotoSeedData("Loft Bedroom with Washed Linen", SceneType.BEDROOM, ViewType.COURTYARD, "BEDROOM_1", false, 0.94, ImageQualityGrade.EXCELLENT, "Serene bedroom alcove with organic washed linen bedding and soft indirect lighting", "Cozy minimalist bed in neutral tones", "Peaceful hygge sleep sanctuary", List.of("queen_bed", "linen_bedding"), List.of("minimalist", "cozy"), 0xb56576, 0xffffff),
                        new PhotoSeedData("Terrazzo & Brass Nordic Bath", SceneType.BATHROOM, ViewType.NONE, "BATHROOM_1", false, 0.91, ImageQualityGrade.GOOD, "Danish terrazzo tiled bathroom with brushed brass fixtures and walk-in rain shower", "Clean terrazzo bathroom with brass details", "Modern Nordic bathroom", List.of("walk_in_shower"), List.of("terrazzo", "brass"), 0x6d597a, 0xffffff),
                        new PhotoSeedData("Motorized Sit-Stand 4K Workstation", SceneType.WORKSPACE, ViewType.COURTYARD, "WORKSPACE", false, 0.97, ImageQualityGrade.EXCELLENT, "Motorized sit-stand oak desk with Herman Miller chair and 4K display", "Ergonomic work from home setup in bright corner", "Professional nomad workspace", List.of("standing_desk", "ergonomic_chair", "monitor", "fast_wifi"), List.of("ergonomic", "remote_work"), 0x355070, 0xffffff),
                        new PhotoSeedData("Parisian Wrought-Iron Balcony", SceneType.BALCONY, ViewType.COURTYARD, "BALCONY", false, 0.90, ImageQualityGrade.GOOD, "Charming wrought-iron balcony with small bistro table overlooking green courtyard", "Balcony overlooking peaceful European courtyard", "Morning coffee balcony", List.of("courtyard_view"), List.of("iron", "balcony"), 0xe56b6f, 0xffffff),
                        new PhotoSeedData("Specialty Pour-Over Coffee Bar Nook", SceneType.OTHER, ViewType.NONE, "COFFEE_BAR", false, 0.93, ImageQualityGrade.EXCELLENT, "Dedicated specialty pour-over and Fellow Stagg coffee bar nook", "Specialty espresso and pour over setup on wood counter", "Coffee lover station", List.of("espresso_machine", "coffee_grinder"), List.of("coffee_bar", "artisanal"), 0xeaac8b, 0x000000),
                        new PhotoSeedData("Quiet Cobblestone Courtyard View", SceneType.VIEW, ViewType.COURTYARD, "VIEW", false, 0.91, ImageQualityGrade.GOOD, "Peaceful view over historic cobblestones and birch trees in courtyard", "View looking down at sunny leafy courtyard", "Calm residential view", List.of("courtyard_view"), List.of("quiet", "garden"), 0xb56576, 0xffffff),
                        new PhotoSeedData("Birch Plywood Acoustic Reading Nook", SceneType.OTHER, ViewType.NONE, "READING_NOOK", false, 0.92, ImageQualityGrade.GOOD, "Bespoke birch plywood reading nook and architectural bookshelves", "Built-in birch plywood seat with books", "Custom architectural joinery", List.of("library_nook"), List.of("birch", "minimalist"), 0x355070, 0xffffff)
                )
        ));

        // 6. Historic Marrakech Riad Courtyard Oasis
        list.add(new PropertySeedData(
                "Riad Jasmine Dar Al-Noor — Historic Courtyard Palace with Tiled Plunge Pool & Rooftop",
                "Authentic 18th-century riad lovingly restored with artisanal zellige tilework, carved cedarwood ceilings, and hand-chiseled plasterwork in the historic Medina of Marrakech. Centerpiece is a serene mosaic courtyard with turquoise plunge pool, citrus trees, fragrant jasmine, and an expansive rooftop terrace with Atlas Mountain views and Berber tented lounge.",
                PropertyType.BOUTIQUE_HOTEL, 6, 3, 3, 280.0, 31.6295, -7.9811,
                "Derb El Cadi 12, Medina", "Marrakech", "Marrakech-Safi", "Morocco", "40000",
                List.of(
                        new PhotoSeedData("Carved Cedar Medina Entrance Door", SceneType.EXTERIOR, ViewType.STREET, "EXTERIOR", false, 0.92, ImageQualityGrade.GOOD, "Intricate hand-carved cedar entrance door in a historic Medina alleyway", "Ancient wooden door in quiet Moroccan alley", "Historic entrance portal", List.of("medina_location"), List.of("moroccan", "historic", "carved_wood"), 0x9a031e, 0xffffff),
                        new PhotoSeedData("Traditional Moroccan Bhou Salon", SceneType.LIVING_ROOM, ViewType.COURTYARD, "LIVING_ROOM", false, 0.95, ImageQualityGrade.EXCELLENT, "Traditional Moroccan bhou salon with velvet cushions and brass lanterns", "Richly decorated salon open to central courtyard", "Authentic Moroccan lounge", List.of("fireplace", "courtyard_view"), List.of("moroccan", "oriental"), 0x5f0f40, 0xffffff),
                        new PhotoSeedData("Candlelit Courtyard Zellige Dining", SceneType.DINING, ViewType.COURTYARD, "DINING", false, 0.93, ImageQualityGrade.EXCELLENT, "Candlelit courtyard dining alcove with mosaic zellige tables under orange trees", "Dining tables set beside indoor courtyard pool", "Atmospheric palace dining", List.of("courtyard_dining"), List.of("zellige", "candlelight"), 0x0f4c5c, 0xffffff),
                        new PhotoSeedData("Medina Kitchen with Clay Tagines", SceneType.KITCHEN, ViewType.NONE, "KITCHEN", false, 0.89, ImageQualityGrade.GOOD, "Traditional Moroccan kitchen with clay tagines and modern amenities", "Kitchen with glazed tiles and brass spice jars", "Spiced culinary kitchen", List.of("coffee_maker"), List.of("traditional"), 0xfb8b24, 0x000000),
                        new PhotoSeedData("Palatial Plasterwork Master Suite", SceneType.BEDROOM, ViewType.COURTYARD, "BEDROOM_1", false, 0.96, ImageQualityGrade.EXCELLENT, "Opulent suite with four-poster bed, ornate plasterwork, and carved fireplace", "Royal riad bedroom with decorative arches", "Palace master suite", List.of("king_bed", "fireplace"), List.of("moroccan_palace", "luxury"), 0xe36414, 0xffffff),
                        new PhotoSeedData("Terracotta & Turquoise Guest Suite", SceneType.BEDROOM, ViewType.COURTYARD, "BEDROOM_2", false, 0.91, ImageQualityGrade.GOOD, "Terracotta and turquoise tiled suite with arched doorways", "Guest room with colorful zellige tile accents", "Charming riad room", List.of("queen_bed"), List.of("zellige", "arched"), 0x0f4c5c, 0xffffff),
                        new PhotoSeedData("Berber Carpet Twin Bedroom", SceneType.BEDROOM, ViewType.NONE, "BEDROOM_3", false, 0.89, ImageQualityGrade.GOOD, "Cozy twin suite with handwoven Beni Ourain Berber wool carpets", "Twin beds on patterned Moroccan rugs", "Artisanal textile room", List.of("twin_beds"), List.of("berber", "textiles"), 0x9a031e, 0xffffff),
                        new PhotoSeedData("Tadelakt & Copper Basin Bathroom", SceneType.BATHROOM, ViewType.NONE, "BATHROOM_1", false, 0.94, ImageQualityGrade.EXCELLENT, "Tadelakt polished lime bathroom with hammered copper basins and walk-in shower", "Plaster bathroom with copper sink and warm tones", "Spa hammam bathroom", List.of("walk_in_shower", "copper_tub"), List.of("tadelakt", "copper"), 0x5f0f40, 0xffffff),
                        new PhotoSeedData("Emerald Zellige Courtyard Plunge Pool", SceneType.POOL, ViewType.COURTYARD, "POOL", true, 0.98, ImageQualityGrade.EXCELLENT, "Emerald green zellige mosaic plunge pool framed by orange trees and lanterns", "Courtyard swimming pool surrounded by Moorish tile arches", "Iconic riad courtyard pool", List.of("plunge_pool", "courtyard_pool"), List.of("zellige", "oasis"), 0x0f4c5c, 0xffffff),
                        new PhotoSeedData("Rooftop Terrace with Berber Tent", SceneType.BALCONY, ViewType.MOUNTAIN, "ROOFTOP", false, 0.95, ImageQualityGrade.EXCELLENT, "Panoramic rooftop terrace with sun loungers and traditional Berber tent lounge", "Rooftop lounge under nomadic fabric tent with rugs", "Scenic riad rooftop", List.of("mountain_view", "rooftop_terrace"), List.of("rooftop", "berber_tent"), 0xfb8b24, 0x000000),
                        new PhotoSeedData("Atlas Mountain Sunset Minaret View", SceneType.VIEW, ViewType.MOUNTAIN, "VIEW", false, 0.96, ImageQualityGrade.EXCELLENT, "Sunset panorama across historic Medina minarets toward snowcapped Atlas Mountains", "Rooftop view of Marrakech skyline and mountains", "Majestic mountain sunset", List.of("atlas_mountain_view"), List.of("sunset", "panorama"), 0x9a031e, 0xffffff)
                )
        ));

        // 7. Ibiza Mediterranean Beachfront Hacienda
        list.add(new PropertySeedData(
                "Casa Cala Salada — Bohemian Beachfront Villa with Direct Private Sea Access & Sun Deck",
                "Barefoot luxury beachfront estate set among fragrant pine groves with direct private path to crystal-clear turquoise waters. Blends traditional Ibicenco whitewashed architecture with bohemian chic interior styling. Features an outdoor summer kitchen with wood-fired pizza oven, teak yoga deck, ocean-facing infinity pool, and sunset cocktail bar.",
                PropertyType.VILLA, 8, 4, 4, 720.0, 38.9812, 1.3021,
                "Camino Cala Salada 8", "Ibiza", "Balearic Islands", "Spain", "07820",
                List.of(
                        new PhotoSeedData("Whitewashed Beach Finca Exterior", SceneType.EXTERIOR, ViewType.SEA_VIEW, "EXTERIOR", false, 0.96, ImageQualityGrade.EXCELLENT, "Whitewashed Ibicenco finca surrounded by ancient olive trees and sea views", "Mediterranean villa overlooking crystal clear bay", "Boho beachfront villa", List.of("sea_view", "beach_access"), List.of("ibicenco", "boho_chic"), 0x0077b6, 0xffffff),
                        new PhotoSeedData("Sunken Boho Ocean Living Room", SceneType.LIVING_ROOM, ViewType.SEA_VIEW, "LIVING_ROOM", false, 0.95, ImageQualityGrade.EXCELLENT, "Sunken bohemian lounge with raw wood tables and panoramic ocean windows", "Living room with built-in seating facing turquoise sea", "Barefoot luxury lounge", List.of("sea_view", "fire_pit"), List.of("boho", "sunken_lounge"), 0x0096c7, 0xffffff),
                        new PhotoSeedData("Bougainvillea Pergola Dining for 10", SceneType.DINING, ViewType.SEA_VIEW, "DINING", false, 0.94, ImageQualityGrade.EXCELLENT, "Bougainvillea-shaded outdoor dining pergola with 10-person rustic teak table", "Outdoor dining table overlooking Mediterranean waters", "Alfresco coastal dining", List.of("sea_view", "large_dining_table"), List.of("outdoor_dining", "pergola"), 0x7209b7, 0xffffff),
                        new PhotoSeedData("Summer Kitchen with Wood-Fired Oven", SceneType.KITCHEN, ViewType.SEA_VIEW, "KITCHEN", false, 0.93, ImageQualityGrade.EXCELLENT, "Open-concept summer kitchen with wood-fired pizza oven and wine cellar", "Outdoor kitchen by the pool with stone pizza oven", "Chef summer kitchen", List.of("pizza_oven", "wine_fridge"), List.of("summer_kitchen", "pizza_oven"), 0xf72585, 0xffffff),
                        new PhotoSeedData("Oceanfront King Suite with Teak Terrace", SceneType.BEDROOM, ViewType.SEA_VIEW, "BEDROOM_1", false, 0.96, ImageQualityGrade.EXCELLENT, "Oceanfront king suite opening directly onto private teak sun terrace", "Master bedroom with floor-to-ceiling sea horizon views", "Luxury coastal bedroom", List.of("king_bed", "sea_view", "terrace_access"), List.of("boho_luxury", "linen"), 0x4361ee, 0xffffff),
                        new PhotoSeedData("Garden Bedroom with Outdoor Shower", SceneType.BEDROOM, ViewType.GARDEN, "BEDROOM_2", false, 0.91, ImageQualityGrade.GOOD, "En-suite double bedroom with private outdoor bamboo rain shower", "Guest bedroom opening into private garden patio", "Private garden bedroom", List.of("queen_bed", "private_terrace"), List.of("outdoor_shower"), 0x4cc9f0, 0x000000),
                        new PhotoSeedData("Rattan Boho Sea View Twin Room", SceneType.BEDROOM, ViewType.SEA_VIEW, "BEDROOM_3", false, 0.90, ImageQualityGrade.GOOD, "Bohemian twin room with woven rattan headboards and ocean view", "Twin beds with coastal textures and sea view", "Breezy twin bedroom", List.of("twin_beds", "sea_view"), List.of("rattan", "boho"), 0x3a0ca3, 0xffffff),
                        new PhotoSeedData("Cozy Garden Casita Bedroom", SceneType.BEDROOM, ViewType.GARDEN, "BEDROOM_4", false, 0.88, ImageQualityGrade.GOOD, "Cozy detached garden casita double room with private pergola", "Guest cottage room surrounded by lavender", "Quiet garden casita", List.of("double_bed"), List.of("casita"), 0x560bad, 0xffffff),
                        new PhotoSeedData("Microcement Ocean View Spa Bath", SceneType.BATHROOM, ViewType.SEA_VIEW, "BATHROOM_1", false, 0.95, ImageQualityGrade.EXCELLENT, "Microcement spa bathroom with freestanding oval stone tub overlooking waves", "Stone bathtub situated before glass wall to the sea", "Luxury coastal spa bath", List.of("freestanding_tub", "sea_view"), List.of("microcement", "stone_tub"), 0x4895ef, 0xffffff),
                        new PhotoSeedData("Horizon Infinity Beachfront Pool", SceneType.POOL, ViewType.SEA_VIEW, "POOL", true, 0.98, ImageQualityGrade.EXCELLENT, "Horizon infinity pool blending seamlessly into the turquoise Mediterranean sea", "Infinity swimming pool edge meeting the ocean water", "Iconic beachfront pool", List.of("infinity_pool", "private_pool", "sea_view", "beach_access"), List.of("infinity_pool", "beachfront"), 0x0077b6, 0xffffff),
                        new PhotoSeedData("Sunset Cocktail Lounge & Daybeds", SceneType.BALCONY, ViewType.SEA_VIEW, "SUN_DECK", false, 0.97, ImageQualityGrade.EXCELLENT, "Sunset cocktail lounge with oversized daybeds overlooking the bay", "Wood sun deck with white daybeds at golden hour", "Premier sunset viewing deck", List.of("sunset_view", "sunbeds"), List.of("daybed", "sunset"), 0xf72585, 0xffffff)
                )
        ));

        // 8. Kyoto Traditional Zen Machiya
        list.add(new PropertySeedData(
                "Gion Kyo-Machiya — Preserved Historic Wooden Townhouse with Zen Garden & Cedar Onsen Tub",
                "Elegantly preserved 120-year-old traditional wooden machiya townhouse in historic Gion. Features authentic tatami-mat rooms, sliding shoji paper screens, exposed natural cedar rafters, a private interior moss and rock zen garden (tsuboniwa), and a master bath featuring an aromatic Japanese hinoki (cypress cedar) soaking onsen tub.",
                PropertyType.HOUSE, 5, 2, 2, 340.0, 35.0037, 135.7772,
                "Gion-machi Minamigawa 570", "Kyoto", "Kyoto Prefecture", "Japan", "605-0074",
                List.of(
                        new PhotoSeedData("Historic Slatted Wooden Koushi Facade", SceneType.EXTERIOR, ViewType.STREET, "EXTERIOR", false, 0.95, ImageQualityGrade.EXCELLENT, "Traditional wooden slatted koushi facade with paper lantern in Gion lane", "Historic Japanese machiya facade with dark wood slats", "Authentic Gion street townhouse", List.of("gion_location", "traditional_facade"), List.of("machiya", "historic_japanese"), 0x3d312a, 0xffffff),
                        new PhotoSeedData("Tatami Living Room with Zen Garden View", SceneType.LIVING_ROOM, ViewType.GARDEN, "LIVING_ROOM", true, 0.96, ImageQualityGrade.EXCELLENT, "Tatami-mat living room with low wooden tea table and zaisu floor chairs", "Tatami room looking onto tranquil interior rock garden", "Zen Japanese living space", List.of("garden_view", "tatami_mats"), List.of("tatami", "zen", "minimalist"), 0x584b42, 0xffffff),
                        new PhotoSeedData("Shoi Screen Courtyard Dining Space", SceneType.DINING, ViewType.GARDEN, "DINING", false, 0.91, ImageQualityGrade.GOOD, "Traditional dining space with shoji screens sliding open to courtyard", "Low dining table beside paper sliding doors", "Minimalist Japanese dining", List.of("garden_view"), List.of("shoji", "zen"), 0x8d7b68, 0xffffff),
                        new PhotoSeedData("Modernized Japanese Kitchen with Matcha Set", SceneType.KITCHEN, ViewType.NONE, "KITCHEN", false, 0.90, ImageQualityGrade.GOOD, "Modernized Japanese kitchen with induction cooktop and handcrafted matcha set", "Clean wood kitchen with ceremonial tea tools", "Functional tea kitchen", List.of("tea_set", "induction"), List.of("minimalist", "modern_japanese"), 0x3e362e, 0xffffff),
                        new PhotoSeedData("Upper Tatami Organic Futon Bedroom", SceneType.BEDROOM, ViewType.GARDEN, "BEDROOM_1", false, 0.95, ImageQualityGrade.EXCELLENT, "Upper floor tatami room with luxury organic Japanese futons and wooden cedar beams", "Authentic tatami sleeping room with futons", "Traditional Japanese bedroom", List.of("organic_futon", "tatami_room"), List.of("futon", "tatami"), 0x4a3f35, 0xffffff),
                        new PhotoSeedData("Wabi-Sabi Queen Bed Room", SceneType.BEDROOM, ViewType.NONE, "BEDROOM_2", false, 0.91, ImageQualityGrade.GOOD, "Western-style queen bed with minimalist Japanese paper lamp lighting", "Minimalist bedroom with Japanese paper lantern glow", "Serene fusion bedroom", List.of("queen_bed", "paper_lamps"), List.of("wabi_sabi"), 0x63584e, 0xffffff),
                        new PhotoSeedData("Aromatic Hinoki Cedar Onsen Soaking Tub", SceneType.BATHROOM, ViewType.GARDEN, "BATHROOM_1", false, 0.98, ImageQualityGrade.EXCELLENT, "Fragrant Japanese hinoki cypress cedar soaking tub with view to moss garden", "Wooden Japanese onsen tub filled with steaming water", "Signature onsen cedar bath", List.of("cedar_tub", "onsen", "garden_view"), List.of("hinoki", "onsen", "cedar"), 0x2b2118, 0xffffff),
                        new PhotoSeedData("Minimalist Stone Tile Rain Shower", SceneType.BATHROOM, ViewType.NONE, "BATHROOM_2", false, 0.90, ImageQualityGrade.GOOD, "Sleek dark stone tile shower room with rain shower and cedar stool", "Japanese wash station with dark slate tiles", "Modern rain shower bath", List.of("rain_shower"), List.of("stone_tile"), 0x1c1917, 0xffffff),
                        new PhotoSeedData("Interior Moss & Rock Tsuboniwa Garden", SceneType.OTHER, ViewType.GARDEN, "ZEN_GARDEN", false, 0.97, ImageQualityGrade.EXCELLENT, "Private interior tsuboniwa courtyard with moss, raked gravel, and bamboo fountain", "Traditional Japanese rock garden with trickling bamboo water", "Meditative zen courtyard", List.of("zen_garden", "bamboo_fountain"), List.of("tsuboniwa", "zen_garden"), 0x3d312a, 0xffffff),
                        new PhotoSeedData("Chashitsu Meditative Tea Ceremony Nook", SceneType.OTHER, ViewType.GARDEN, "TEA_ROOM", false, 0.94, ImageQualityGrade.EXCELLENT, "Dedicated Japanese chashitsu tea ceremony nook with calligraphy scroll", "Intimate tea room with low ceiling and tatami", "Traditional tea sanctuary", List.of("tea_ceremony"), List.of("chashitsu", "tea_room"), 0x584b42, 0xffffff),
                        new PhotoSeedData("Historic Exposed Joinery & Antique Tansu", SceneType.OTHER, ViewType.NONE, "JOINERY", false, 0.92, ImageQualityGrade.GOOD, "Exposed 120-year-old wooden joinery, cedar posts, and restored antique tansu chest", "Historic craftsmanship and traditional cabinetry", "Artisanal woodwork detail", List.of("antique_furniture"), List.of("historic_joinery"), 0x2b2118, 0xffffff)
                )
        ));

        // 9. Scottish Highland Lochside Castle Suite
        list.add(new PropertySeedData(
                "Eilean Castle Lodge — Historic Baronial Castle Suite with Loch Views & Antique Fireplace",
                "Romantic baronial castle suite set on the mist-shrouded shores of Loch Ness in the Scottish Highlands. Features 16th-century stone masonry, grand antique wood-burning fireplace, hand-bound leather library room, tartan velvet upholstery, four-poster oak canopy bed, and panoramic views of the dark loch waters and heather-covered hills.",
                PropertyType.BOUTIQUE_HOTEL, 4, 2, 2, 490.0, 57.3229, -4.4244,
                "Loch Ness Shoreline Estate", "Inverness", "Highland", "United Kingdom", "IV63 6XG",
                List.of(
                        new PhotoSeedData("Scottish Baronial Stone Castle Exterior", SceneType.EXTERIOR, ViewType.SEA_VIEW, "EXTERIOR", true, 0.97, ImageQualityGrade.EXCELLENT, "Historic Scottish baronial stone castle with turrets on the misty loch shore", "Stone castle with towers reflected in dark loch waters", "Romantic Scottish castle facade", List.of("castle_turret", "loch_view"), List.of("scottish_castle", "baronial", "historic"), 0x1f2421, 0xffffff),
                        new PhotoSeedData("Grand Stone Hearth Drawing Room", SceneType.LIVING_ROOM, ViewType.SEA_VIEW, "LIVING_ROOM", false, 0.96, ImageQualityGrade.EXCELLENT, "Grand drawing room with antique stone hearth, plaid velvet sofa, and oil paintings", "Castle salon with burning log fire and loch view", "Highland castle drawing room", List.of("fireplace", "loch_view", "stone_hearth"), List.of("baronial", "antique"), 0x335c67, 0xffffff),
                        new PhotoSeedData("Baronial Candelabra Dining Hall", SceneType.DINING, ViewType.SEA_VIEW, "DINING", false, 0.93, ImageQualityGrade.EXCELLENT, "Formal baronial dining room with candelabra and antique mahogany table", "Castle dining table with candlelight and loch panorama", "Historic baronial dining", List.of("candelabra", "antique_table"), List.of("formal", "baronial"), 0x212529, 0xffffff),
                        new PhotoSeedData("Farmhouse Kitchen with British AGA Stove", SceneType.KITCHEN, ViewType.NONE, "KITCHEN", false, 0.91, ImageQualityGrade.GOOD, "Country farmhouse kitchen with British cast-iron AGA stove and copper cookware", "Warm kitchen with cream AGA cooker and stone floor", "Highland country kitchen", List.of("aga_stove", "copper_pots"), List.of("farmhouse", "country"), 0x4a4e69, 0xffffff),
                        new PhotoSeedData("Four-Poster Oak Canopy Master Suite", SceneType.BEDROOM, ViewType.SEA_VIEW, "BEDROOM_1", false, 0.96, ImageQualityGrade.EXCELLENT, "Four-poster carved oak canopy bed with tartan throws and panoramic loch view", "Grand four poster bed in stone walled bedroom", "Romantic castle bedchamber", List.of("four_poster_bed", "loch_view"), List.of("canopy_bed", "historic"), 0x343a40, 0xffffff),
                        new PhotoSeedData("Antique Brass Guest Bedroom", SceneType.BEDROOM, ViewType.GARDEN, "BEDROOM_2", false, 0.89, ImageQualityGrade.GOOD, "Exposed stone walled bedroom with antique brass double bed and woolen blankets", "Guest bedroom with brass bedstead and tartan curtains", "Cozy highland bedroom", List.of("brass_bed"), List.of("stone_wall", "antique"), 0x495057, 0xffffff),
                        new PhotoSeedData("Victorian Cast-Iron Clawfoot Bath", SceneType.BATHROOM, ViewType.SEA_VIEW, "BATHROOM_1", false, 0.95, ImageQualityGrade.EXCELLENT, "Victorian clawfoot cast-iron bathtub with brass fittings overlooking the loch", "Freestanding clawfoot tub before window with lake view", "Historic Victorian bathroom", List.of("clawfoot_tub", "loch_view"), List.of("victorian", "clawfoot"), 0x1f2421, 0xffffff),
                        new PhotoSeedData("Antique Leather Library & Study", SceneType.WORKSPACE, ViewType.SEA_VIEW, "WORKSPACE", false, 0.95, ImageQualityGrade.EXCELLENT, "Antique mahogany writing desk with leather wingback armchairs and rare books", "Wood paneled library with desk and view of water", "Scholarly castle study", List.of("library", "writing_desk", "armchairs"), List.of("antique_library", "workspace"), 0x212529, 0xffffff),
                        new PhotoSeedData("Castle Turret Lookout Platform", SceneType.BALCONY, ViewType.SEA_VIEW, "TURRET", false, 0.96, ImageQualityGrade.EXCELLENT, "Castle stone turret lookout platform overlooking the dark misty waters of the loch", "Stone battlement turret with panoramic lake view", "Historic turret balcony", List.of("panoramic_loch_view"), List.of("turret", "lookout"), 0x335c67, 0xffffff),
                        new PhotoSeedData("Misty Highland Loch & Heather Panorama", SceneType.VIEW, ViewType.SEA_VIEW, "VIEW", false, 0.96, ImageQualityGrade.EXCELLENT, "Misty morning panorama of Loch Ness and rolling heather-covered hills", "Dramatic moody view of Scottish loch at dawn", "Iconic highland landscape", List.of("loch_ness_view", "scenic_landscape"), List.of("misty_loch", "highlands"), 0x1f2421, 0xffffff),
                        new PhotoSeedData("Private Stone Jetty on Loch Ness", SceneType.OTHER, ViewType.SEA_VIEW, "JETTY", false, 0.93, ImageQualityGrade.GOOD, "Private stone jetty extending into the loch for kayak and boat excursions", "Stone pier jutting into calm deep water", "Private water access", List.of("boat_dock", "lake_access"), List.of("private_jetty"), 0x343a40, 0xffffff)
                )
        ));

        // 10. Tuscan Vineyard Hilltop Farmhouse
        list.add(new PropertySeedData(
                "Podere dell'Olivo — Restored 17th-Century Tuscan Stone Farmhouse with Private Vineyard",
                "Idyllic 17th-century rustic stone farmhouse set atop the rolling cypress-lined hills of Val d'Orcia in Tuscany. Surrounded by private Sangiovese vineyards and olive groves. Features terracotta tile floors, chestnut wood beamed ceilings, large rustic open hearth fireplace, professional wine tasting cellar, outdoor dining pergola, and infinity pool overlooking the hills.",
                PropertyType.HOUSE, 8, 4, 3, 580.0, 43.0642, 11.6033,
                "Strada Provinciale del Brunello 22", "Siena", "Tuscany", "Italy", "53024",
                List.of(
                        new PhotoSeedData("Tuscan Stone Farmhouse with Cypress Trees", SceneType.EXTERIOR, ViewType.GARDEN, "EXTERIOR", true, 0.97, ImageQualityGrade.EXCELLENT, "Rustic stone farmhouse framed by iconic tall Tuscan cypress trees and vineyards", "Stone villa surrounded by rolling green hills and cypress drive", "Classic Tuscan estate facade", List.of("cypress_trees", "vineyard_view"), List.of("tuscan", "stone_farmhouse", "rustic"), 0x588157, 0xffffff),
                        new PhotoSeedData("Vaulted Terracotta Ceiling Salon", SceneType.LIVING_ROOM, ViewType.GARDEN, "LIVING_ROOM", false, 0.95, ImageQualityGrade.EXCELLENT, "Warm rustic salon with vaulted terracotta ceiling and grand stone fireplace", "Tuscan living room with terracotta arches and leather armchairs", "Inviting rustic farmhouse lounge", List.of("stone_fireplace", "vaulted_ceiling"), List.of("rustic_tuscan", "terracotta"), 0xbc6c25, 0xffffff),
                        new PhotoSeedData("Long Chestnut Table Farmhouse Dining", SceneType.DINING, ViewType.GARDEN, "DINING", false, 0.92, ImageQualityGrade.GOOD, "Farmhouse dining room with long chestnut wood table and wrought iron chandelier", "Dining hall with long timber table overlooking vineyard", "Rustic Italian dining", List.of("large_dining_table"), List.of("farmhouse", "chestnut"), 0xdda15e, 0x000000),
                        new PhotoSeedData("Traditional Tuscan Chef Kitchen", SceneType.KITCHEN, ViewType.NONE, "KITCHEN", false, 0.94, ImageQualityGrade.EXCELLENT, "Traditional Tuscan chef kitchen with carved stone sink, marble counter, and pasta station", "Rustic Italian kitchen with open shelving and copper pans", "Authentic culinary kitchen", List.of("chef_kitchen", "marble_counter"), List.of("tuscan_chef", "stone_sink"), 0x6f4e37, 0xffffff),
                        new PhotoSeedData("Romantic Vineyard View Country Suite", SceneType.BEDROOM, ViewType.GARDEN, "BEDROOM_1", false, 0.95, ImageQualityGrade.EXCELLENT, "Romantic country suite with wrought-iron king bed and vineyard views", "Master bedroom with wooden ceiling beams looking over vineyard", "Peaceful country bedroom", List.of("king_bed", "vineyard_view"), List.of("tuscan_country", "romantic"), 0x3a5a40, 0xffffff),
                        new PhotoSeedData("Sunlit Twin Bedroom with Wooden Shutters", SceneType.BEDROOM, ViewType.GARDEN, "BEDROOM_2", false, 0.89, ImageQualityGrade.GOOD, "Sunlit twin bedroom with floral linen and green wooden shutters", "Twin room with shuttered windows open to olive trees", "Charming country twin room", List.of("twin_beds"), List.of("wooden_shutters"), 0xa3b18a, 0x000000),
                        new PhotoSeedData("Exposed Stone Wall Double Bedroom", SceneType.BEDROOM, ViewType.NONE, "BEDROOM_3", false, 0.90, ImageQualityGrade.GOOD, "Rustic double bedroom with exposed ancient stone walls and terra cotta tiles", "Double bedroom in authentic stone wall setting", "Historic stone bedroom", List.of("double_bed"), List.of("exposed_stone"), 0x344e41, 0xffffff),
                        new PhotoSeedData("Ground-Floor Lavender Courtyard Suite", SceneType.BEDROOM, ViewType.GARDEN, "BEDROOM_4", false, 0.91, ImageQualityGrade.GOOD, "Ground-floor garden suite opening directly to fragrant lavender courtyard", "Bedroom with French doors leading to stone patio", "Garden terrace bedroom", List.of("queen_bed", "courtyard_access"), List.of("courtyard_suite"), 0x588157, 0xffffff),
                        new PhotoSeedData("Travertine Marble Freestanding Bath", SceneType.BATHROOM, ViewType.GARDEN, "BATHROOM_1", false, 0.94, ImageQualityGrade.EXCELLENT, "Travertine marble bathroom with freestanding roll-top bath overlooking olive grove", "Tuscan bathroom with stone bathtub and scenic window", "Natural stone spa bath", List.of("freestanding_tub", "travertine"), List.of("travertine", "marble"), 0xdda15e, 0x000000),
                        new PhotoSeedData("Wisteria-Draped Sunset Dining Pergola", SceneType.BALCONY, ViewType.GARDEN, "PERGOLA", false, 0.96, ImageQualityGrade.EXCELLENT, "Wisteria-draped outdoor pergola overlooking rolling hills at sunset", "Outdoor dining patio with climbing flowers and sunset view", "Sunset vineyard dining terrace", List.of("outdoor_dining", "sunset_view"), List.of("pergola", "sunset_dining"), 0xbc6c25, 0xffffff),
                        new PhotoSeedData("Saltwater Vineyard Infinity Pool", SceneType.POOL, ViewType.GARDEN, "POOL", false, 0.98, ImageQualityGrade.EXCELLENT, "Saltwater infinity pool surrounded by olive trees and Sangiovese vineyard rows", "Pool edge blending into the rolling hills of Val d'Orcia", "Panoramic vineyard pool", List.of("infinity_pool", "private_pool", "vineyard_view"), List.of("infinity_pool", "vineyard_pool"), 0x283618, 0xffffff)
                )
        ));

        return list;
    }

    private List<VisionEvalQuery> buildEvaluationQueries(List<Property> properties) {
        // Map created properties by index:
        // 0: Santorini, 1: Zermatt, 2: Manhattan, 3: Ubud, 4: Copenhagen,
        // 5: Marrakech, 6: Ibiza, 7: Kyoto, 8: Inverness, 9: Siena
        UUID pSantorini = properties.get(0).getId();
        UUID pZermatt = properties.get(1).getId();
        UUID pManhattan = properties.get(2).getId();
        UUID pUbud = properties.get(3).getId();
        UUID pCopenhagen = properties.get(4).getId();
        UUID pMarrakech = properties.get(5).getId();
        UUID pIbiza = properties.get(6).getId();
        UUID pKyoto = properties.get(7).getId();
        UUID pInverness = properties.get(8).getId();
        UUID pSiena = properties.get(9).getId();

        List<VisionEvalQuery> qList = new ArrayList<>();

        // ==========================================
        // 12 TEXT_ONLY QUERIES
        // ==========================================
        qList.add(VisionEvalQuery.builder()
                .queryText("luxury cliffside villa with infinity pool overlooking the sea")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pSantorini.toString(), pIbiza.toString())))
                .relevanceGradesJson(toJson(Map.of(pSantorini.toString(), 3, pIbiza.toString(), 2, pSiena.toString(), 1)))
                .notes("Tests cliffside/infinity pool sea-view retrieval")
                .build());

        qList.add(VisionEvalQuery.builder()
                .queryText("cozy rustic alpine chalet with stone wood fireplace and snow")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pZermatt.toString(), pInverness.toString())))
                .relevanceGradesJson(toJson(Map.of(pZermatt.toString(), 3, pInverness.toString(), 2, pSiena.toString(), 1)))
                .notes("Tests alpine mountain chalet & fireplace retrieval")
                .build());

        qList.add(VisionEvalQuery.builder()
                .queryText("modern glass penthouse with skyscraper skyline view and chef kitchen")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pManhattan.toString())))
                .relevanceGradesJson(toJson(Map.of(pManhattan.toString(), 3, pCopenhagen.toString(), 1)))
                .notes("Tests modern glass penthouse skyscraper aesthetic")
                .build());

        qList.add(VisionEvalQuery.builder()
                .queryText("open-air tropical bamboo villa with private jungle river pool")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pUbud.toString())))
                .relevanceGradesJson(toJson(Map.of(pUbud.toString(), 3, pIbiza.toString(), 1)))
                .notes("Tests tropical bamboo jungle architecture retrieval")
                .build());

        qList.add(VisionEvalQuery.builder()
                .queryText("bright Scandinavian minimalist apartment with dedicated sit-stand desk workspace")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pCopenhagen.toString(), pManhattan.toString())))
                .relevanceGradesJson(toJson(Map.of(pCopenhagen.toString(), 3, pManhattan.toString(), 2)))
                .notes("Tests Scandinavian interior & ergonomic workstation retrieval")
                .build());

        qList.add(VisionEvalQuery.builder()
                .queryText("historic Moroccan riad with interior tiled courtyard and plunge pool")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pMarrakech.toString())))
                .relevanceGradesJson(toJson(Map.of(pMarrakech.toString(), 3)))
                .notes("Tests historic Moorish/Moroccan riad architecture")
                .build());

        qList.add(VisionEvalQuery.builder()
                .queryText("bohemian beachfront hacienda with private Mediterranean sea access")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pIbiza.toString(), pSantorini.toString())))
                .relevanceGradesJson(toJson(Map.of(pIbiza.toString(), 3, pSantorini.toString(), 2)))
                .notes("Tests beachfront bohemian Mediterranean villa retrieval")
                .build());

        qList.add(VisionEvalQuery.builder()
                .queryText("traditional Japanese machiya with tatami mats and cedar onsen bath")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pKyoto.toString())))
                .relevanceGradesJson(toJson(Map.of(pKyoto.toString(), 3)))
                .notes("Tests traditional Japanese tatami and onsen bath retrieval")
                .build());

        qList.add(VisionEvalQuery.builder()
                .queryText("historic Scottish castle suite with antique library and loch view")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pInverness.toString(), pZermatt.toString())))
                .relevanceGradesJson(toJson(Map.of(pInverness.toString(), 3, pZermatt.toString(), 1)))
                .notes("Tests castle baronial estate & antique library retrieval")
                .build());

        qList.add(VisionEvalQuery.builder()
                .queryText("rustic stone farmhouse surrounded by private vineyards and olive groves")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pSiena.toString(), pIbiza.toString())))
                .relevanceGradesJson(toJson(Map.of(pSiena.toString(), 3, pIbiza.toString(), 1)))
                .notes("Tests rustic Tuscan farmhouse & vineyard retrieval")
                .build());

        qList.add(VisionEvalQuery.builder()
                .queryText("luxury property with freestanding soaking tub and scenic view")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pSantorini.toString(), pManhattan.toString(), pKyoto.toString(), pIbiza.toString())))
                .relevanceGradesJson(toJson(Map.of(pSantorini.toString(), 3, pManhattan.toString(), 3, pKyoto.toString(), 3, pIbiza.toString(), 2)))
                .notes("Tests amenity-based aesthetic query across multiple properties")
                .build());

        qList.add(VisionEvalQuery.builder()
                .queryText("romantic getaway with outdoor private hot tub or heated pool")
                .queryType("TEXT_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pSantorini.toString(), pZermatt.toString(), pIbiza.toString(), pSiena.toString())))
                .relevanceGradesJson(toJson(Map.of(pSantorini.toString(), 3, pZermatt.toString(), 3, pIbiza.toString(), 3, pSiena.toString(), 2)))
                .notes("Tests lifestyle romantic vacation retrieval with pool/jacuzzi")
                .build());

        // ==========================================
        // 9 IMAGE_ONLY QUERIES
        // ==========================================
        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pSantorini.toString().substring(0, 8) + "-11.jpg")
                .queryType("IMAGE_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pSantorini.toString(), pIbiza.toString())))
                .relevanceGradesJson(toJson(Map.of(pSantorini.toString(), 3, pIbiza.toString(), 2)))
                .notes("Image query: Cliffside infinity pool over caldera")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pZermatt.toString().substring(0, 8) + "-1.jpg")
                .queryType("IMAGE_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pZermatt.toString(), pInverness.toString())))
                .relevanceGradesJson(toJson(Map.of(pZermatt.toString(), 3, pInverness.toString(), 1)))
                .notes("Image query: Snow-covered timber alpine chalet facade")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pManhattan.toString().substring(0, 8) + "-2.jpg")
                .queryType("IMAGE_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pManhattan.toString())))
                .relevanceGradesJson(toJson(Map.of(pManhattan.toString(), 3)))
                .notes("Image query: Double-height glass living room with skyline")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pUbud.toString().substring(0, 8) + "-1.jpg")
                .queryType("IMAGE_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pUbud.toString())))
                .relevanceGradesJson(toJson(Map.of(pUbud.toString(), 3)))
                .notes("Image query: Curved bamboo architecture in rainforest")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pCopenhagen.toString().substring(0, 8) + "-7.jpg")
                .queryType("IMAGE_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pCopenhagen.toString(), pManhattan.toString())))
                .relevanceGradesJson(toJson(Map.of(pCopenhagen.toString(), 3, pManhattan.toString(), 2)))
                .notes("Image query: Ergonomic sit-stand desk workspace")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pMarrakech.toString().substring(0, 8) + "-9.jpg")
                .queryType("IMAGE_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pMarrakech.toString())))
                .relevanceGradesJson(toJson(Map.of(pMarrakech.toString(), 3)))
                .notes("Image query: Mosaic zellige courtyard plunge pool")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pIbiza.toString().substring(0, 8) + "-10.jpg")
                .queryType("IMAGE_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pIbiza.toString(), pSantorini.toString())))
                .relevanceGradesJson(toJson(Map.of(pIbiza.toString(), 3, pSantorini.toString(), 2)))
                .notes("Image query: Beachfront Mediterranean infinity pool")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pKyoto.toString().substring(0, 8) + "-7.jpg")
                .queryType("IMAGE_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pKyoto.toString())))
                .relevanceGradesJson(toJson(Map.of(pKyoto.toString(), 3)))
                .notes("Image query: Cedar hinoki onsen soaking tub with garden")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pSiena.toString().substring(0, 8) + "-1.jpg")
                .queryType("IMAGE_ONLY")
                .expectedPropertyIdsJson(toJson(List.of(pSiena.toString(), pIbiza.toString())))
                .relevanceGradesJson(toJson(Map.of(pSiena.toString(), 3, pIbiza.toString(), 1)))
                .notes("Image query: Tuscan stone farmhouse framed by cypress trees")
                .build());

        // ==========================================
        // 9 MULTIMODAL QUERIES
        // ==========================================
        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pSantorini.toString().substring(0, 8) + "-11.jpg")
                .queryText("cliffside villa in Greece under $700")
                .queryType("MULTIMODAL")
                .city("Santorini")
                .maxPricePerNight(700.0)
                .expectedPropertyIdsJson(toJson(List.of(pSantorini.toString())))
                .relevanceGradesJson(toJson(Map.of(pSantorini.toString(), 3)))
                .notes("Multimodal: Cliffside pool image + Greece geo/price constraint")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pZermatt.toString().substring(0, 8) + "-1.jpg")
                .queryText("chalet with fireplace and ski access in the Swiss Alps")
                .queryType("MULTIMODAL")
                .city("Zermatt")
                .maxPricePerNight(900.0)
                .expectedPropertyIdsJson(toJson(List.of(pZermatt.toString())))
                .relevanceGradesJson(toJson(Map.of(pZermatt.toString(), 3)))
                .notes("Multimodal: Snowy chalet image + Zermatt constraint")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pManhattan.toString().substring(0, 8) + "-2.jpg")
                .queryText("modern luxury penthouse in New York with skyline views")
                .queryType("MULTIMODAL")
                .city("New York")
                .expectedPropertyIdsJson(toJson(List.of(pManhattan.toString())))
                .relevanceGradesJson(toJson(Map.of(pManhattan.toString(), 3)))
                .notes("Multimodal: Skyline living room image + NY constraint")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pUbud.toString().substring(0, 8) + "-1.jpg")
                .queryText("eco-luxury curved bamboo retreat in Ubud Bali")
                .queryType("MULTIMODAL")
                .city("Ubud")
                .expectedPropertyIdsJson(toJson(List.of(pUbud.toString())))
                .relevanceGradesJson(toJson(Map.of(pUbud.toString(), 3)))
                .notes("Multimodal: Bamboo image + Ubud Bali constraint")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pCopenhagen.toString().substring(0, 8) + "-7.jpg")
                .queryText("bright studio loft in Copenhagen with dedicated workspace for remote nomad")
                .queryType("MULTIMODAL")
                .city("Copenhagen")
                .maxPricePerNight(250.0)
                .expectedPropertyIdsJson(toJson(List.of(pCopenhagen.toString())))
                .relevanceGradesJson(toJson(Map.of(pCopenhagen.toString(), 3)))
                .notes("Multimodal: Workstation image + Copenhagen price constraint")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pMarrakech.toString().substring(0, 8) + "-9.jpg")
                .queryText("traditional riad in Marrakech Medina with plunge pool")
                .queryType("MULTIMODAL")
                .city("Marrakech")
                .expectedPropertyIdsJson(toJson(List.of(pMarrakech.toString())))
                .relevanceGradesJson(toJson(Map.of(pMarrakech.toString(), 3)))
                .notes("Multimodal: Courtyard pool image + Marrakech Medina")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pIbiza.toString().substring(0, 8) + "-10.jpg")
                .queryText("beachfront villa in Ibiza Spain with 4 bedrooms for group stay")
                .queryType("MULTIMODAL")
                .city("Ibiza")
                .minGuests(8)
                .expectedPropertyIdsJson(toJson(List.of(pIbiza.toString())))
                .relevanceGradesJson(toJson(Map.of(pIbiza.toString(), 3)))
                .notes("Multimodal: Beachfront pool image + guest count filter")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pKyoto.toString().substring(0, 8) + "-7.jpg")
                .queryText("historic Japanese townhouse with zen garden and onsen cedar bath in Kyoto")
                .queryType("MULTIMODAL")
                .city("Kyoto")
                .expectedPropertyIdsJson(toJson(List.of(pKyoto.toString())))
                .relevanceGradesJson(toJson(Map.of(pKyoto.toString(), 3)))
                .notes("Multimodal: Hinoki tub image + Kyoto constraint")
                .build());

        qList.add(VisionEvalQuery.builder()
                .referenceImageKey("properties/gt-" + pInverness.toString().substring(0, 8) + "-1.jpg")
                .queryText("historic castle lodge on Loch Ness in Inverness Scotland")
                .queryType("MULTIMODAL")
                .city("Inverness")
                .expectedPropertyIdsJson(toJson(List.of(pInverness.toString())))
                .relevanceGradesJson(toJson(Map.of(pInverness.toString(), 3)))
                .notes("Multimodal: Castle image + Inverness Scotland constraint")
                .build());

        return qList;
    }
}
