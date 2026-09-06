package com.luna.aggarly.vision.search;

import com.luna.aggarly.property.entity.Amenity;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.vision.entity.PropertyVisualProfile;
import com.luna.aggarly.vision.repository.PropertyVisualProfileRepository;
import com.luna.aggarly.vision.search.records.AggregatedPropertyScore;
import com.luna.aggarly.vision.search.records.SearchMode;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheapMLReranker {

    private final PropertyRepository propertyRepository;
    private final PropertyVisualProfileRepository profileRepository;

    private static final Set<String> COMMON_FEATURE_KEYWORDS = Set.of(
            "pool", "infinity", "swim", "ocean", "sea", "beach", "beachfront", "cliffside",
            "balcony", "terrace", "patio", "deck", "jacuzzi", "hot tub", "spa", "bath", "bathtub",
            "workspace", "desk", "office", "wifi", "kitchen", "chef", "garden", "courtyard",
            "fireplace", "view", "panoramic", "modern", "luxury", "rustic", "bohemian", "minimalist",
            "cave", "villa", "chalet", "riad", "machiya", "castle", "apartment", "cabin", "farmhouse", "penthouse"
    );

    public List<AggregatedPropertyScore> rerank(List<AggregatedPropertyScore> candidates, VisionSearchQuery query, int limit) {
        log.debug("Cheap ML Reranker evaluating {} candidates", candidates.size());
        if (candidates == null || candidates.isEmpty()) return List.of();

        List<AggregatedPropertyScore> scored = new ArrayList<>();
        Set<String> queryKeywords = extractKeywords(query.rawText());

        for (AggregatedPropertyScore cand : candidates) {
            Property prop = propertyRepository.findById(cand.propertyId()).orElse(null);
            PropertyVisualProfile profile = profileRepository.findByPropertyId(cand.propertyId()).orElse(null);

            float fAmenity = computeAmenityScore(cand, prop, profile, queryKeywords, query.searchMode());
            float fScene = computeSceneScore(cand, queryKeywords);
            float fQuality = computeQualityScore(prop, profile);
            float fStyle = computeStyleScore(prop, profile, queryKeywords);

            // Composite ML scoring model:
            // 65% base aggregated visual similarity + 15% amenity + 10% scene + 5% style + 5% quality
            float mlScore = (cand.aggregatedScore() * 0.65f)
                    + (fAmenity * 0.15f)
                    + (fScene * 0.10f)
                    + (fStyle * 0.05f)
                    + (fQuality * 0.05f);

            scored.add(new AggregatedPropertyScore(
                    cand.propertyId(),
                    cand.targetSceneScore(),
                    cand.descriptionChannelScore(),
                    mlScore,
                    cand.matchingImages(),
                    cand.bestMatchImageId(),
                    cand.matchedSceneTypes(),
                    cand.roomDiversityBonus(),
                    cand.coveragePenalty(),
                    cand.sceneSummary()
            ));
        }

        scored.sort((a, b) -> Float.compare(b.aggregatedScore(), a.aggregatedScore()));

        if (scored.size() > limit) {
            return scored.subList(0, limit);
        }
        return scored;
    }

    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        if (text == null || text.isBlank()) return keywords;

        String[] tokens = text.toLowerCase(Locale.ROOT).split("[\\s,;:.!?\"'()\\-]+");
        for (String token : tokens) {
            if (COMMON_FEATURE_KEYWORDS.contains(token)) {
                keywords.add(token);
            }
        }
        return keywords;
    }

    private float computeAmenityScore(AggregatedPropertyScore cand, Property prop, PropertyVisualProfile profile,
                                      Set<String> queryKeywords, SearchMode mode) {
        if (mode == SearchMode.IMAGE_ONLY || queryKeywords.isEmpty()) {
            int strongScenes = 0;
            if (cand.matchedSceneTypes() != null) {
                if (cand.matchedSceneTypes().contains("POOL")) strongScenes++;
                if (cand.matchedSceneTypes().contains("VIEW")) strongScenes++;
                if (cand.matchedSceneTypes().contains("BALCONY")) strongScenes++;
                if (cand.matchedSceneTypes().contains("EXTERIOR")) strongScenes++;
            }
            return Math.min(1.0f, 0.60f + (strongScenes * 0.10f));
        }

        if (prop == null) return 0.50f;

        int matches = 0;
        String titleDesc = ((prop.getTitle() != null ? prop.getTitle() : "") + " " +
                (prop.getDescription() != null ? prop.getDescription() : "")).toLowerCase(Locale.ROOT);

        String profileAmenities = profile != null && profile.getAggregatedAmenitiesJson() != null ?
                profile.getAggregatedAmenitiesJson().toLowerCase(Locale.ROOT) : "";

        Set<String> propAmenities = new HashSet<>();
        if (prop.getAmenities() != null) {
            for (Amenity a : prop.getAmenities()) {
                if (a.getName() != null) {
                    propAmenities.addAll(Arrays.asList(a.getName().toLowerCase(Locale.ROOT).split("[\\s_]+")));
                }
            }
        }

        for (String kw : queryKeywords) {
            if (titleDesc.contains(kw) || profileAmenities.contains(kw) || propAmenities.contains(kw)) {
                matches++;
            }
        }

        return Math.min(1.0f, (float) matches / Math.max(1, queryKeywords.size()));
    }

    private float computeSceneScore(AggregatedPropertyScore cand, Set<String> queryKeywords) {
        float bestFused = !cand.matchingImages().isEmpty() ? cand.matchingImages().get(0).fusedScore() : 0.5f;
        float diversity = cand.matchedSceneTypes() != null ? Math.min(1.0f, cand.matchedSceneTypes().size() / 4.0f) : 0.5f;

        float intentBoost = 0.0f;
        if (cand.matchedSceneTypes() != null) {
            for (String kw : queryKeywords) {
                String upper = kw.toUpperCase(Locale.ROOT);
                if (cand.matchedSceneTypes().contains(upper)) {
                    intentBoost += 0.15f;
                }
            }
        }

        return Math.min(1.0f, (0.60f * bestFused) + (0.25f * diversity) + intentBoost);
    }

    private float computeQualityScore(Property prop, PropertyVisualProfile profile) {
        float ratingNorm = 0.85f;
        if (prop != null && prop.getAvgRating() != null && prop.getAvgRating().floatValue() > 0) {
            ratingNorm = Math.min(1.0f, prop.getAvgRating().floatValue() / 5.0f);
        }

        float coverage = 0.80f;
        if (profile != null && profile.getCoverageScore() != null && profile.getCoverageScore() > 0) {
            coverage = Math.min(1.0f, profile.getCoverageScore().floatValue());
        }

        return (0.60f * ratingNorm) + (0.40f * coverage);
    }

    private float computeStyleScore(Property prop, PropertyVisualProfile profile, Set<String> queryKeywords) {
        if (profile == null) return 0.70f;

        float score = 0.70f;
        if (queryKeywords.contains("luxury") && profile.getLuxuryScore() != null) {
            score = Math.max(score, profile.getLuxuryScore().floatValue());
        }
        if (queryKeywords.contains("romantic") && profile.getRomanticScore() != null) {
            score = Math.max(score, profile.getRomanticScore().floatValue());
        }
        if ((queryKeywords.contains("cozy") || queryKeywords.contains("family")) && profile.getFamilyScore() != null) {
            score = Math.max(score, profile.getFamilyScore().floatValue());
        }
        if (queryKeywords.contains("relax") && profile.getRelaxationScore() != null) {
            score = Math.max(score, profile.getRelaxationScore().floatValue());
        }

        return Math.min(1.0f, score);
    }
}
