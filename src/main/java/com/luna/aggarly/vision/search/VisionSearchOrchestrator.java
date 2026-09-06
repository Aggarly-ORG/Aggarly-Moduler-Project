package com.luna.aggarly.vision.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.filestorage.service.FileStorageService;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyImageRepository;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.entity.PropertyVisualProfile;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import com.luna.aggarly.vision.repository.PropertyVisualProfileRepository;
import com.luna.aggarly.vision.search.records.AggregatedPropertyScore;
import com.luna.aggarly.vision.search.records.ChannelSearchResult;
import com.luna.aggarly.vision.search.records.FusedImageCandidate;
import com.luna.aggarly.vision.search.records.StageRankingDiagnostics;
import com.luna.aggarly.vision.search.records.ThreeChannelResults;
import com.luna.aggarly.vision.search.records.VisionSearchExecution;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionSearchOrchestrator {

    private final VisionQueryEmbedder queryEmbedder;
    private final QdrantVisionSearchService qdrantSearchService;
    private final ScoreFusionService scoreFusionService;
    private final SearchCandidateMerger candidateMerger;
    private final PropertyScoreAggregator scoreAggregator;
    private final VisionContextualFilter contextualFilter;
    private final CheapMLReranker cheapMlReranker;
    private final CrossModalVisualReranker visualReranker;
    private final VisionPaginationService paginationService;
    private final PropertyRepository propertyRepository;
    private final PropertyImageRepository imageRepository;
    private final PropertyImageAiMetadataRepository metadataRepository;
    private final PropertyVisualProfileRepository profileRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final ScoreCalibrator scoreCalibrator;

    @Transactional(readOnly = true)
    public List<VisionSearchResult> search(VisionSearchQuery query) {
        return searchWithDiagnostics(query).results();
    }

    @Transactional(readOnly = true)
    public VisionSearchExecution searchWithDiagnostics(VisionSearchQuery query) {
        log.info("Executing Vision Search query: text='{}', mode={}, pageSize={}",
                query.rawText(), query.searchMode(), query.pageSize());

        Map<String, Long> latencies = new LinkedHashMap<>();

        long t0 = System.nanoTime();
        VisionQueryEmbedder.QueryVectors queryVectors = queryEmbedder.embed(query);
        latencies.put("embedding", (System.nanoTime() - t0) / 1_000_000);

        long t1 = System.nanoTime();
        ThreeChannelResults channelResults = qdrantSearchService.searchAllChannels(queryVectors, query.hardFilters());
        latencies.put("qdrant_retrieval", (System.nanoTime() - t1) / 1_000_000);

        List<UUID> imageChannelRanking = channelResults.imageChannel() != null ?
                channelResults.imageChannel().stream()
                        .sorted((a, b) -> Float.compare(b.score(), a.score()))
                        .map(ChannelSearchResult::propertyId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList() : List.of();

        List<UUID> captionChannelRanking = channelResults.captionChannel() != null ?
                channelResults.captionChannel().stream()
                        .sorted((a, b) -> Float.compare(b.score(), a.score()))
                        .map(ChannelSearchResult::propertyId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList() : List.of();

        List<UUID> descriptionChannelRanking = channelResults.descriptionChannel() != null ?
                channelResults.descriptionChannel().stream()
                        .sorted((a, b) -> Float.compare(b.score(), a.score()))
                        .map(ChannelSearchResult::propertyId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList() : List.of();

        // Stage 3: Score fusion across image and caption vectors
        long t2 = System.nanoTime();
        List<FusedImageCandidate> fusedImages = scoreFusionService.fuse(channelResults, query.searchMode());
        latencies.put("score_fusion", (System.nanoTime() - t2) / 1_000_000);

        List<UUID> fusedRanking = fusedImages.stream()
                .map(FusedImageCandidate::propertyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Collect all candidate property UUIDs from both image channels and description channel
        Set<UUID> candidatePropertyIds = new HashSet<>();
        for (FusedImageCandidate img : fusedImages) {
            candidatePropertyIds.add(img.propertyId());
        }
        if (channelResults.descriptionChannel() != null) {
            for (var descRes : channelResults.descriptionChannel()) {
                candidatePropertyIds.add(descRes.propertyId());
            }
        }

        // Stage 4: Strict business constraint & availability barrier
        long t3 = System.nanoTime();
        List<Property> validProperties = candidateMerger.filterAndMergeCandidates(candidatePropertyIds, query.hardFilters());
        Set<UUID> validPropertyIds = validProperties.stream().map(Property::getId).collect(Collectors.toSet());

        // Filter out fused images from properties that failed hard constraints
        List<FusedImageCandidate> validFusedImages = fusedImages.stream()
                .filter(img -> validPropertyIds.contains(img.propertyId()))
                .toList();

        var validDescResults = channelResults.descriptionChannel() != null ? channelResults.descriptionChannel().stream()
                .filter(res -> validPropertyIds.contains(res.propertyId()))
                .toList() : List.<ChannelSearchResult>of();

        // Stage 5: Category-Aware Property Score Aggregation
        List<AggregatedPropertyScore> aggregatedScores = scoreAggregator.aggregate(
                validFusedImages, validDescResults, query.hardFilters()
        );

        List<UUID> aggregatedRanking = aggregatedScores.stream()
                .map(AggregatedPropertyScore::propertyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Stage 6: Contextual & Usability Filter
        List<AggregatedPropertyScore> filtered = contextualFilter.filter(aggregatedScores, query);
        latencies.put("aggregation_and_filtering", (System.nanoTime() - t3) / 1_000_000);

        // Stage 7: Cheap ML Reranker (Stage 1 fast ranking)
        long t4 = System.nanoTime();
        List<AggregatedPropertyScore> mlReranked = cheapMlReranker.rerank(filtered, query, 20);
        latencies.put("ml_reranking", (System.nanoTime() - t4) / 1_000_000);

        List<UUID> mlRerankedRanking = mlReranked.stream()
                .map(AggregatedPropertyScore::propertyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Stage 8: Optional LLM Reranker (Stage 2 deep explanation)
        long t5 = System.nanoTime();
        var rerankedItems = visualReranker.rerank(mlReranked, query);
        latencies.put("llm_reranking", (System.nanoTime() - t5) / 1_000_000);

        List<UUID> finalRanking = rerankedItems.stream()
                .map(item -> item.propertyScore().propertyId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Stage 9: Final Response Pagination & Entity Hydration
        List<VisionSearchResult> results = rerankedItems.stream()
                .limit(query.pageSize())
                .map(this::toSearchResult)
                .toList();

        StageRankingDiagnostics diagnostics = new StageRankingDiagnostics(
                imageChannelRanking,
                captionChannelRanking,
                descriptionChannelRanking,
                fusedRanking,
                aggregatedRanking,
                mlRerankedRanking,
                finalRanking,
                latencies
        );

        return new VisionSearchExecution(results, diagnostics);
    }

    private VisionSearchResult toSearchResult(CrossModalVisualReranker.RerankedItem item) {
        AggregatedPropertyScore cand = item.propertyScore();
        Property prop = propertyRepository.findById(cand.propertyId()).orElse(null);
        PropertyVisualProfile profile = profileRepository.findByPropertyId(cand.propertyId()).orElse(null);

        String title = prop != null ? prop.getTitle() : "Listing";
        String city = (prop != null && prop.getAddress() != null) ? prop.getAddress().getCity() : "";
        String country = (prop != null && prop.getAddress() != null) ? prop.getAddress().getCountry() : "";
        Double price = prop != null && prop.getBasePricePerNight() != null ? prop.getBasePricePerNight().doubleValue() : 0.0;
        Integer guests = prop != null ? prop.getMaxGuests() : 2;

        UUID bestImageId = cand.bestMatchImageId();
        String imageUrl = null;
        String sceneType = "BEDROOM";

        if (bestImageId != null) {
            var imgOpt = imageRepository.findById(bestImageId);
            if (imgOpt.isPresent()) {
                imageUrl = fileStorageService.resolveUrl(imgOpt.get().getObjectKey());
            }
            var metaOpt = metadataRepository.findByPropertyImageId(bestImageId);
            if (metaOpt.isPresent() && metaOpt.get().getSceneType() != null) {
                sceneType = metaOpt.get().getSceneType().name();
            }
        }

        List<String> amenities = new ArrayList<>();
        List<String> styleTags = new ArrayList<>();
        if (profile != null && profile.getAggregatedAmenitiesJson() != null) {
            try {
                amenities = objectMapper.readValue(profile.getAggregatedAmenitiesJson(), List.class);
            } catch (Exception ignored) {}
        }
        if (profile != null && profile.getAggregatedStyleTagsJson() != null) {
            try {
                styleTags = objectMapper.readValue(profile.getAggregatedStyleTagsJson(), List.class);
            } catch (Exception ignored) {}
        }

        float calibratedFinal = scoreCalibrator.calibrate(item.finalScore());
        float calibratedVisual = scoreCalibrator.calibrate(cand.targetSceneScore());
        float calibratedDesc = cand.descriptionChannelScore() > 0 ? scoreCalibrator.calibrate(cand.descriptionChannelScore()) : 0.0f;

        return new VisionSearchResult(
                cand.propertyId(),
                title,
                city,
                country,
                price,
                guests,
                calibratedFinal,
                calibratedVisual,
                calibratedDesc,
                bestImageId,
                imageUrl,
                sceneType,
                item.explanation(),
                amenities,
                styleTags,
                profile != null && profile.getCoverageScore() != null ? profile.getCoverageScore() : 1.0
        );
    }
}
