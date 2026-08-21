package com.luna.aggarly.vision.search;

import com.luna.aggarly.vision.search.records.ChannelSearchResult;
import com.luna.aggarly.vision.search.records.ThreeChannelResults;
import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import com.luna.aggarly.vision.vector.QdrantVisionClient;
import com.luna.aggarly.vision.vector.records.QdrantCondition;
import com.luna.aggarly.vision.vector.records.QdrantFilter;
import com.luna.aggarly.vision.vector.records.QdrantSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Executes parallel multi-channel search across Qdrant multi-vector collections
 * with logical minimum score thresholds to filter out noisy vectors.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QdrantVisionSearchService {

    private final QdrantVisionClient qdrantClient;

    @Value("${aggarly.qdrant.collections.images:property_images_v1}")
    private String imagesCollection = "property_images_v1";

    @Value("${aggarly.qdrant.collections.descriptions:property_descriptions_v1}")
    private String descriptionsCollection = "property_descriptions_v1";

    @Value("${aggarly.vision.search.image-channel-limit:150}")
    private int imageChannelLimit = 150;

    @Value("${aggarly.vision.search.caption-channel-limit:150}")
    private int captionChannelLimit = 150;

    @Value("${aggarly.vision.search.description-channel-limit:50}")
    private int descriptionChannelLimit = 50;

    @Value("${aggarly.vision.search.channel-min-score.image:0.15}")
    private float imageChannelMinScore = 0.15f;

    @Value("${aggarly.vision.search.channel-min-score.caption:0.20}")
    private float captionChannelMinScore = 0.20f;

    @Value("${aggarly.vision.search.channel-min-score.description:0.20}")
    private float descriptionChannelMinScore = 0.20f;

    public ThreeChannelResults searchAllChannels(VisionQueryEmbedder.QueryVectors vectors, VisionSearchFilters filters) {
        QdrantFilter imageFilter = buildImageFilter(filters);
        QdrantFilter descFilter = buildDescriptionFilter(filters);

        CompletableFuture<List<ChannelSearchResult>> channelAFuture = CompletableFuture.supplyAsync(() ->
                searchChannel(imagesCollection, "image_vector", vectors.imageVector(), imageChannelLimit, imageFilter, imageChannelMinScore));

        CompletableFuture<List<ChannelSearchResult>> channelBFuture = CompletableFuture.supplyAsync(() ->
                searchChannel(imagesCollection, "caption_vector", vectors.textVector(), captionChannelLimit, imageFilter, captionChannelMinScore));

        CompletableFuture<List<ChannelSearchResult>> channelCFuture = CompletableFuture.supplyAsync(() ->
                searchDescriptionChannel(descriptionsCollection, vectors.textVector(), descriptionChannelLimit, descFilter, descriptionChannelMinScore));

        CompletableFuture.allOf(channelAFuture, channelBFuture, channelCFuture).join();

        try {
            return new ThreeChannelResults(
                    channelAFuture.get(),
                    channelBFuture.get(),
                    channelCFuture.get()
            );
        } catch (Exception e) {
            log.error("Error aggregating multi-channel search results: {}", e.getMessage(), e);
            return new ThreeChannelResults(List.of(), List.of(), List.of());
        }
    }

    private List<ChannelSearchResult> searchChannel(String collection, String vectorName, float[] queryVec, int limit, QdrantFilter filter, float minScore) {
        List<QdrantSearchResult> rawResults = qdrantClient.searchByNamedVector(
                collection, vectorName, queryVec, limit, filter, minScore
        );

        List<ChannelSearchResult> channelResults = new ArrayList<>();
        for (QdrantSearchResult sr : rawResults) {
            UUID imageId = sr.id();
            UUID propertyId = sr.id();
            if (sr.payload() != null && sr.payload().containsKey("propertyId")) {
                try {
                    propertyId = UUID.fromString(sr.payload().get("propertyId").toString());
                } catch (Exception ignored) {}
            }
            channelResults.add(new ChannelSearchResult(imageId, propertyId, sr.score(), vectorName, sr.payload()));
        }
        return channelResults;
    }

    private List<ChannelSearchResult> searchDescriptionChannel(String collection, float[] queryVec, int limit, QdrantFilter filter, float minScore) {
        List<QdrantSearchResult> rawResults = qdrantClient.searchByNamedVector(
                collection, "description_vector", queryVec, limit, filter, minScore
        );

        List<ChannelSearchResult> channelResults = new ArrayList<>();
        for (QdrantSearchResult sr : rawResults) {
            UUID propertyId = sr.id();
            channelResults.add(new ChannelSearchResult(null, propertyId, sr.score(), "description_vector", sr.payload()));
        }
        return channelResults;
    }

    private QdrantFilter buildImageFilter(VisionSearchFilters filters) {
        return null;
    }

    private QdrantFilter buildDescriptionFilter(VisionSearchFilters filters) {
        List<QdrantCondition> conditions = new ArrayList<>();
        if (filters != null && filters.city() != null && !filters.city().isBlank()) {
            conditions.add(QdrantCondition.equals("city", filters.city()));
        }
        return conditions.isEmpty() ? null : QdrantFilter.must(conditions);
    }
}
