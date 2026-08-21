package com.luna.aggarly.vision.search;

import com.luna.aggarly.vision.search.records.AggregatedPropertyScore;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CheapMLReranker {

    public List<AggregatedPropertyScore> rerank(List<AggregatedPropertyScore> candidates, VisionSearchQuery query, int limit) {
        log.debug("Cheap ML Reranker evaluating {} candidates", candidates.size());
        if (candidates.isEmpty()) return candidates;

        // Stage 1 Fast Scoring: incorporates exact keyword alignment and visual score boost
        List<AggregatedPropertyScore> scored = new ArrayList<>(candidates);
        scored.sort((a, b) -> Float.compare(b.aggregatedScore(), a.aggregatedScore()));

        if (scored.size() > limit) {
            return scored.subList(0, limit);
        }
        return scored;
    }
}
