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
import com.luna.aggarly.vision.search.records.FusedImageCandidate;
import com.luna.aggarly.vision.search.records.ThreeChannelResults;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    @Transactional(readOnly = true)
    public List<VisionSearchResult> search(VisionSearchQuery query) {
        log.info("Executing Vision Search query: text='{}', mode={}, pageSize={}",
                query.rawText(), query.searchMode(), query.pageSize());

        // Stage 1: Compute query vectors
        VisionQueryEmbedder.QueryVectors queryVectors = queryEmbedder.embed(query);

        // Stage 2: Three-channel parallel Qdrant retrieval
        ThreeChannelResults channelResults = qdrantSearchService.searchAllChannels(queryVectors, query.hardFilters());

        // Stage 3: Score fusion across image and caption vectors
        List<FusedImageCandidate> fusedImages = scoreFusionService.fuse(channelResults, query.searchMode());

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
        List<Property> validProperties = candidateMerger.filterAndMergeCandidates(candidatePropertyIds, query.hardFilters());
        Set<UUID> validPropertyIds = validProperties.stream().map(Property::getId).collect(Collectors.toSet());

        // Filter out fused images from properties that failed hard constraints
        List<FusedImageCandidate> validFusedImages = fusedImages.stream()
                .filter(img -> validPropertyIds.contains(img.propertyId()))
                .toList();

        var validDescResults = channelResults.descriptionChannel().stream()
                .filter(res -> validPropertyIds.contains(res.propertyId()))
                .toList();

        // Stage 5: Category-Aware Property Score Aggregation
        List<AggregatedPropertyScore> aggregatedScores = scoreAggregator.aggregate(
                validFusedImages, validDescResults, query.hardFilters()
        );

        // Stage 6: Contextual & Usability Filter
        List<AggregatedPropertyScore> filtered = contextualFilter.filter(aggregatedScores, query);

        // Stage 7: Cheap ML Reranker (Stage 1 fast ranking)
        List<AggregatedPropertyScore> mlReranked = cheapMlReranker.rerank(filtered, query, 20);

        // Stage 8: Optional LLM Reranker (Stage 2 deep explanation)
        var rerankedItems = visualReranker.rerank(mlReranked, query);

        // Stage 9: Cursor Pagination & Result Formatting
        VisionPaginationService.CursorData cursorData = paginationService.decodeCursor(query.cursor());
        int startOffset = cursorData.offset();
        int endOffset = Math.min(startOffset + query.pageSize(), rerankedItems.size());

        List<VisionSearchResult> finalResults = new ArrayList<>();
        if (startOffset < rerankedItems.size()) {
            var pagedItems = rerankedItems.subList(startOffset, endOffset);
            for (var item : pagedItems) {
                finalResults.add(buildSearchResultDto(item));
            }
        }

        log.info("Vision Search completed. Returning {} results (total matching: {})", finalResults.size(), rerankedItems.size());
        return finalResults;
    }

    private VisionSearchResult buildSearchResultDto(CrossModalVisualReranker.RerankedItem item) {
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

        return new VisionSearchResult(
                cand.propertyId(),
                title,
                city,
                country,
                price,
                guests,
                item.finalScore(),
                cand.targetSceneScore(),
                cand.descriptionChannelScore(),
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
