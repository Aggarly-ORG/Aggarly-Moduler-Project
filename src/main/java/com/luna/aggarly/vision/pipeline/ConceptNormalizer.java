package com.luna.aggarly.vision.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.vision.pipeline.records.DetectedAmenity;
import com.luna.aggarly.vision.pipeline.records.DetectedObject;
import com.luna.aggarly.vision.pipeline.records.NormalizedVisionResult;
import com.luna.aggarly.vision.pipeline.records.StyleTag;
import com.luna.aggarly.vision.pipeline.records.VisionInferenceResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConceptNormalizer {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private Map<String, String> conceptMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            Resource resource = resourceLoader.getResource("classpath:vision/vision-concept-map.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    conceptMap = objectMapper.readValue(is, new TypeReference<Map<String, String>>() {});
                    log.info("Loaded {} concept normalization mappings", conceptMap.size());
                }
            }
        } catch (Exception e) {
            log.warn("Could not load vision concept map, using inline mappings: {}", e.getMessage());
        }
    }

    public NormalizedVisionResult normalize(VisionInferenceResult raw) {
        // 1. Normalize Style Tags
        List<StyleTag> normalizedTags = new ArrayList<>();
        Set<String> seenTags = new HashSet<>();

        if (raw.styleTags() != null) {
            for (StyleTag tag : raw.styleTags()) {
                String rawTagName = tag.tag().toLowerCase(Locale.ROOT).trim();
                String canonical = conceptMap.getOrDefault(rawTagName, rawTagName);

                if (!seenTags.contains(canonical)) {
                    seenTags.add(canonical);
                    normalizedTags.add(new StyleTag(canonical, tag.confidence(), tag.sourceImageId(), "VISION"));
                }
            }
        }

        // Filter contradictory tags (e.g. minimalist vs maximalist, dark vs bright)
        filterContradictions(normalizedTags);

        // 2. Normalize Amenities
        List<DetectedAmenity> normalizedAmenities = new ArrayList<>();
        Set<String> seenAmenities = new HashSet<>();

        if (raw.detectedAmenities() != null) {
            for (DetectedAmenity amenity : raw.detectedAmenities()) {
                String rawName = amenity.amenityName().toLowerCase(Locale.ROOT).trim();
                String canonical = conceptMap.getOrDefault(rawName, rawName);

                if (!seenAmenities.contains(canonical)) {
                    seenAmenities.add(canonical);
                    normalizedAmenities.add(new DetectedAmenity(canonical, amenity.confidence(), amenity.sourceImageId(), "VISION"));
                }
            }
        }

        // 3. Filter Objects
        List<DetectedObject> filteredObjects = new ArrayList<>();
        if (raw.detectedObjects() != null) {
            for (DetectedObject obj : raw.detectedObjects()) {
                if (obj.confidence() >= 0.65) {
                    filteredObjects.add(obj);
                }
            }
        }

        // 4. Semantic Scene Disambiguation & Guardrails (e.g. Balcony vs Bedroom)
        String correctedScene = raw.sceneType();
        String correctedView = raw.viewType();
        Boolean correctedIndoor = raw.isIndoor();

        String combinedText = ((raw.aiCaption() != null ? raw.aiCaption() : "") + " " +
                (raw.altText() != null ? raw.altText() : "") + " " +
                String.join(" ", seenTags) + " " +
                String.join(" ", seenAmenities)).toLowerCase(Locale.ROOT);

        boolean hasBalconyCues = combinedText.contains("balcony") || combinedText.contains("terrace") ||
                combinedText.contains("patio") || combinedText.contains("veranda") ||
                combinedText.contains("railing") || combinedText.contains("wicker") ||
                combinedText.contains("outdoor") || combinedText.contains("sunset") ||
                combinedText.contains("ocean view") || combinedText.contains("sea view");

        boolean hasActualBed = combinedText.contains("king bed") || combinedText.contains("queen bed") ||
                combinedText.contains("headboard") || combinedText.contains("bedsheet") ||
                combinedText.contains("mattress");

        if (hasBalconyCues && !hasActualBed) {
            if ("BEDROOM".equalsIgnoreCase(correctedScene) || "LIVING_ROOM".equalsIgnoreCase(correctedScene) || "OTHER".equalsIgnoreCase(correctedScene)) {
                correctedScene = "BALCONY";
                correctedIndoor = false;
            }
        }

        if (combinedText.contains("sea") || combinedText.contains("ocean") || combinedText.contains("coast") || combinedText.contains("water horizon")) {
            correctedView = "SEA_VIEW";
            seenAmenities.add("sea_view");
            if (normalizedAmenities.stream().noneMatch(a -> a.amenityName().equalsIgnoreCase("sea_view"))) {
                normalizedAmenities.add(new DetectedAmenity("sea_view", 0.95, null, "NORMALIZER"));
            }
        }

        if (hasBalconyCues) {
            seenAmenities.add("balcony");
            if (normalizedAmenities.stream().noneMatch(a -> a.amenityName().equalsIgnoreCase("balcony"))) {
                normalizedAmenities.add(new DetectedAmenity("balcony", 0.95, null, "NORMALIZER"));
            }
        }

        VisionInferenceResult correctedRaw = new VisionInferenceResult(
                correctedScene != null ? correctedScene.toUpperCase(Locale.ROOT) : "BALCONY",
                raw.sceneConfidence() != 0 ? raw.sceneConfidence() : 0.95,
                correctedIndoor != null ? correctedIndoor : false,
                correctedView != null ? correctedView.toUpperCase(Locale.ROOT) : "SEA_VIEW",
                raw.aiCaption(),
                raw.altText(),
                raw.ocrText(),
                raw.detectedObjects(),
                normalizedAmenities,
                normalizedTags,
                raw.dominantColors(),
                raw.conditionAssessment(),
                raw.specialFeatures(),
                raw.moderationStatus(),
                raw.moderationReason(),
                raw.classificationModelName(),
                raw.classificationModelVersion(),
                raw.promptVersion(),
                raw.preprocessingVersion()
        );

        // 5. Derive Visual Fit Suitability Scores (not objective claims, but heuristic lifestyle fit)
        Map<String, Double> visualFitScores = calculateVisualFit(seenTags, seenAmenities, correctedScene);

        // 6. Cluster ID helper
        String roomClusterId = correctedScene != null ? "cluster-" + correctedScene.toLowerCase(Locale.ROOT) : "cluster-general";

        return new NormalizedVisionResult(
                correctedRaw,
                normalizedTags,
                normalizedAmenities,
                filteredObjects,
                visualFitScores,
                roomClusterId
        );
    }

    private void filterContradictions(List<StyleTag> tags) {
        // e.g. if both dark and bright exist, keep higher confidence
        StyleTag darkTag = findTag(tags, "dark");
        StyleTag brightTag = findTag(tags, "bright");
        if (darkTag != null && brightTag != null) {
            if (darkTag.confidence() >= brightTag.confidence()) {
                tags.remove(brightTag);
            } else {
                tags.remove(darkTag);
            }
        }
    }

    private StyleTag findTag(List<StyleTag> tags, String target) {
        for (StyleTag tag : tags) {
            if (tag.tag().equalsIgnoreCase(target)) {
                return tag;
            }
        }
        return null;
    }

    private Map<String, Double> calculateVisualFit(Set<String> tags, Set<String> amenities, String sceneType) {
        Map<String, Double> fit = new HashMap<>();

        double romantic = 0.10;
        double luxury = 0.10;
        double family = 0.10;
        double business = 0.10;
        double relaxation = 0.10;

        if (tags.contains("luxury") || tags.contains("modern") || tags.contains("panoramic_window")) {
            luxury += 0.50;
        }
        if (amenities.contains("private_pool") || amenities.contains("pool") || amenities.contains("jacuzzi")) {
            luxury += 0.25;
            relaxation += 0.40;
            romantic += 0.20;
        }
        if (amenities.contains("fireplace") || tags.contains("cozy") || tags.contains("rustic")) {
            romantic += 0.45;
            relaxation += 0.30;
        }
        if (amenities.contains("workspace") || amenities.contains("standing_desk")) {
            business += 0.70;
        }
        if ("WORKSPACE".equalsIgnoreCase(sceneType)) {
            business += 0.50;
        }
        if (tags.contains("spacious") || amenities.contains("garden") || amenities.contains("balcony")) {
            family += 0.40;
            relaxation += 0.25;
        }

        fit.put("romantic", Math.min(1.0, romantic));
        fit.put("luxury", Math.min(1.0, luxury));
        fit.put("family", Math.min(1.0, family));
        fit.put("business", Math.min(1.0, business));
        fit.put("relaxation", Math.min(1.0, relaxation));

        return fit;
    }
}
