package com.luna.aggarly.vision.search;

import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiReferenceImageSearchService {

    private final ImageSimilaritySearchService imageSearchService;

    public List<VisionSearchResult> findSimilarByMultipleImages(List<String> objectKeys, String textRefinement, VisionSearchFilters filters, int topK) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return List.of();
        }

        Map<UUID, VisionSearchResult> mergedResults = new HashMap<>();

        for (String key : objectKeys) {
            List<VisionSearchResult> singleResults = imageSearchService.findSimilarByImageKey(key, textRefinement, filters, topK);
            for (VisionSearchResult res : singleResults) {
                if (!mergedResults.containsKey(res.propertyId()) || res.finalScore() > mergedResults.get(res.propertyId()).finalScore()) {
                    mergedResults.put(res.propertyId(), res);
                }
            }
        }

        List<VisionSearchResult> sorted = new ArrayList<>(mergedResults.values());
        sorted.sort((a, b) -> Float.compare(b.finalScore(), a.finalScore()));

        if (sorted.size() > topK) {
            return sorted.subList(0, topK);
        }
        return sorted;
    }
}
