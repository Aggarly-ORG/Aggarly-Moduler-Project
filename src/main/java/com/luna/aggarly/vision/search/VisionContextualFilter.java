package com.luna.aggarly.vision.search;

import com.luna.aggarly.vision.search.records.AggregatedPropertyScore;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class VisionContextualFilter {

    public List<AggregatedPropertyScore> filter(List<AggregatedPropertyScore> candidates, VisionSearchQuery query) {
        float minScore = query.minScore();
        List<AggregatedPropertyScore> passed = new ArrayList<>();

        for (AggregatedPropertyScore prop : candidates) {
            if (prop.aggregatedScore() >= minScore) {
                passed.add(prop);
            }
        }

        return passed;
    }
}
