package com.luna.aggarly.vision.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.vision.entity.VisionEvalQuery;
import com.luna.aggarly.vision.entity.VisionEvaluationRun;
import com.luna.aggarly.vision.evaluation.records.EvaluationConfig;
import com.luna.aggarly.vision.evaluation.records.EvaluationReport;
import com.luna.aggarly.vision.evaluation.records.QueryEvalResult;
import com.luna.aggarly.vision.evaluation.records.StageEvaluationReport;
import com.luna.aggarly.vision.evaluation.records.StageLatency;
import com.luna.aggarly.vision.repository.VisionEvalQueryRepository;
import com.luna.aggarly.vision.repository.VisionEvaluationRunRepository;
import com.luna.aggarly.vision.search.SearchCandidateMerger;
import com.luna.aggarly.vision.search.VisionSearchOrchestrator;
import com.luna.aggarly.vision.search.records.StageRankingDiagnostics;
import com.luna.aggarly.vision.search.records.VisionSearchExecution;
import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisionSearchEvaluator {

    private final VisionEvalQueryRepository evalQueryRepository;
    private final VisionEvaluationRunRepository evaluationRunRepository;
    private final VisionSearchOrchestrator searchOrchestrator;
    private final SearchCandidateMerger candidateMerger;
    private final PropertyRepository propertyRepository;
    private final ObjectMapper objectMapper;
    private final com.luna.aggarly.filestorage.service.FileStorageService fileStorageService;

    public EvaluationReport evaluate(EvaluationConfig config) {
        log.info("Starting Vision Search Evaluation with config: {}", config);

        List<VisionEvalQuery> queries = evalQueryRepository.findAll();
        if (queries.isEmpty()) {
            queries = seedSampleEvalQueries();
        }

        if (config.sampleLimit() > 0 && queries.size() > config.sampleLimit()) {
            queries = queries.subList(0, config.sampleLimit());
        }

        List<QueryEvalResult> queryResults = new ArrayList<>();
        Map<String, Integer> channelAttributionCounts = new HashMap<>();

        double sumNdcg5 = 0.0, sumNdcg10 = 0.0, sumRecall10 = 0.0, sumPrec5 = 0.0, sumMrr = 0.0;
        int filterCorrectCount = 0;

        double sumStageImage = 0, sumStageCaption = 0, sumStageDesc = 0, sumStageFused = 0;
        double sumStageAgg = 0, sumStageMl = 0, sumStageLlm = 0;
        Map<String, Long> accumulatedStageLatencies = new HashMap<>();

        for (VisionEvalQuery q : queries) {
            SingleQueryEvaluation sqe = evaluateSingleQuery(q, config);
            queryResults.add(sqe.evalResult());

            sumNdcg5 += sqe.evalResult().ndcgAt5();
            sumNdcg10 += sqe.evalResult().ndcgAt10();
            sumRecall10 += sqe.evalResult().recallAt10();
            sumPrec5 += sqe.evalResult().precisionAt5();
            sumMrr += sqe.evalResult().reciprocalRank();
            if (sqe.evalResult().filterCorrect()) filterCorrectCount++;

            channelAttributionCounts.put(sqe.evalResult().topChannel(), channelAttributionCounts.getOrDefault(sqe.evalResult().topChannel(), 0) + 1);

            sumStageImage += sqe.imageNdcg();
            sumStageCaption += sqe.captionNdcg();
            sumStageDesc += sqe.descNdcg();
            sumStageFused += sqe.fusedNdcg();
            sumStageAgg += sqe.aggregatedNdcg();
            sumStageMl += sqe.mlRerankedNdcg();
            sumStageLlm += sqe.finalNdcg();

            sqe.stageLatencies().forEach((stage, dur) ->
                    accumulatedStageLatencies.merge(stage, dur, Long::sum));
        }

        int total = queries.size();
        double meanNdcg5 = total > 0 ? sumNdcg5 / total : 0.0;
        double meanNdcg10 = total > 0 ? sumNdcg10 / total : 0.0;
        double meanRecall10 = total > 0 ? sumRecall10 / total : 0.0;
        double meanPrec5 = total > 0 ? sumPrec5 / total : 0.0;
        double meanMrr = total > 0 ? sumMrr / total : 0.0;
        double filterCorrectness = total > 0 ? (double) filterCorrectCount / total : 1.0;

        Map<String, Double> channelAttribution = new HashMap<>();
        for (Map.Entry<String, Integer> entry : channelAttributionCounts.entrySet()) {
            channelAttribution.put(entry.getKey(), total > 0 ? (double) entry.getValue() / total : 0.0);
        }

        long totalDurationMs = 0;
        for (QueryEvalResult qr : queryResults) {
            if (qr.latencies() != null) {
                for (StageLatency sl : qr.latencies()) {
                    if ("total_query".equals(sl.stageName())) {
                        totalDurationMs += sl.durationMs();
                    }
                }
            }
        }
        long avgQueryLatencyMs = total > 0 ? totalDurationMs / total : 0;

        Map<String, Long> avgStageLatencies = new LinkedHashMap<>();
        accumulatedStageLatencies.forEach((stage, sumDur) ->
                avgStageLatencies.put(stage, total > 0 ? sumDur / total : 0L));
        avgStageLatencies.put("average_total_query_latency", avgQueryLatencyMs);

        StageEvaluationReport stageReport = new StageEvaluationReport(
                total > 0 ? sumStageImage / total : 0.0,
                total > 0 ? sumStageCaption / total : 0.0,
                total > 0 ? sumStageDesc / total : 0.0,
                total > 0 ? sumStageFused / total : 0.0,
                total > 0 ? sumStageAgg / total : 0.0,
                total > 0 ? sumStageMl / total : 0.0,
                total > 0 ? sumStageLlm / total : 0.0,
                avgStageLatencies
        );

        EvaluationReport report = new EvaluationReport(
                "1.0.0",
                Instant.now(),
                total,
                meanNdcg5,
                meanNdcg10,
                meanRecall10,
                meanPrec5,
                meanMrr,
                filterCorrectness,
                channelAttribution,
                stageReport,
                queryResults
        );

        // Persist evaluation run record
        try {
            VisionEvaluationRun run = VisionEvaluationRun.builder()
                    .pipelineVersion("1.0.0")
                    .evaluatedAt(Instant.now())
                    .totalQueries(total)
                    .ndcgAt5(meanNdcg5)
                    .ndcgAt10(meanNdcg10)
                    .recallAt5(meanPrec5)
                    .recallAt10(meanRecall10)
                    .precisionAt5(meanPrec5)
                    .mrr(meanMrr)
                    .filterCorrectness(filterCorrectness)
                    .fullReportJson(objectMapper.writeValueAsString(report))
                    .build();
            evaluationRunRepository.save(run);
        } catch (Exception e) {
            log.warn("Could not save VisionEvaluationRun entity: {}", e.getMessage());
        }

        log.info("Vision Search Evaluation completed. Mean NDCG@10: {}, Filter Correctness: {}", meanNdcg10, filterCorrectness);
        return report;
    }

    private record SingleQueryEvaluation(
            QueryEvalResult evalResult,
            double imageNdcg,
            double captionNdcg,
            double descNdcg,
            double fusedNdcg,
            double aggregatedNdcg,
            double mlRerankedNdcg,
            double finalNdcg,
            Map<String, Long> stageLatencies
    ) {}

    private SingleQueryEvaluation evaluateSingleQuery(VisionEvalQuery q, EvaluationConfig config) {
        long start = System.currentTimeMillis();

        VisionSearchFilters filters = new VisionSearchFilters(
                q.getCity(), null, null, null, q.getMinGuests(), q.getMaxPricePerNight(), null, null, null
        );

        byte[] refBytes = null;
        if (q.getReferenceImageKey() != null && !q.getReferenceImageKey().isBlank()) {
            try (java.io.InputStream is = fileStorageService.getFileStream(q.getReferenceImageKey())) {
                if (is != null) {
                    refBytes = is.readAllBytes();
                }
            } catch (Exception e) {
                log.debug("Could not read reference image stream for key {}: {}", q.getReferenceImageKey(), e.getMessage());
            }

            if (refBytes == null || refBytes.length == 0) {
                refBytes = loadFallbackImageBytes(q.getReferenceImageKey(), parseExpectedIds(q.getExpectedPropertyIdsJson()));
            }
        }

        int targetTopK = config.topK() > 0 ? config.topK() : 10;
        int effectiveTopK = Math.max(targetTopK, 10);

        VisionSearchQuery searchQuery;
        String queryType = q.getQueryType() != null ? q.getQueryType().toUpperCase() : "TEXT_ONLY";
        if ("IMAGE_ONLY".equals(queryType)) {
            searchQuery = VisionSearchQuery.imageQuery(refBytes, q.getReferenceImageKey(), filters, effectiveTopK, null, null, true);
        } else if ("MULTIMODAL".equals(queryType)) {
            searchQuery = VisionSearchQuery.multimodalQuery(refBytes, q.getReferenceImageKey(), q.getQueryText(), 0.60f, filters, effectiveTopK, null, null, true);
        } else {
            searchQuery = VisionSearchQuery.textQuery(q.getQueryText(), filters, effectiveTopK, null);
        }

        VisionSearchExecution execution = searchOrchestrator.searchWithDiagnostics(searchQuery);
        List<VisionSearchResult> results = execution.results();
        StageRankingDiagnostics diag = execution.diagnostics();
        long duration = System.currentTimeMillis() - start;

        List<UUID> retrievedIds = results.stream().map(VisionSearchResult::propertyId).toList();

        // Extract relevance grades
        Map<UUID, Integer> gradeMap = parseGrades(q.getRelevanceGradesJson());
        List<UUID> expectedIds = parseExpectedIds(q.getExpectedPropertyIdsJson());

        double ndcg5 = computeNdcg(retrievedIds, gradeMap, 5);
        double ndcg10 = computeNdcg(retrievedIds, gradeMap, 10);
        double recall10 = computeRecall(retrievedIds, expectedIds, 10);
        double prec5 = computePrecision(retrievedIds, expectedIds, 5);
        double mrr = computeMrr(retrievedIds, expectedIds);

        // Compute true per-stage NDCG@10
        double imageNdcg = computeNdcg(diag.imageChannelRanking(), gradeMap, 10);
        double captionNdcg = computeNdcg(diag.captionChannelRanking(), gradeMap, 10);
        double descNdcg = computeNdcg(diag.descriptionChannelRanking(), gradeMap, 10);
        double fusedNdcg = computeNdcg(diag.fusedRanking(), gradeMap, 10);
        double aggNdcg = computeNdcg(diag.aggregatedRanking(), gradeMap, 10);
        double mlNdcg = computeNdcg(diag.mlRerankedRanking(), gradeMap, 10);
        double finalNdcg = computeNdcg(diag.finalRanking(), gradeMap, 10);

        // Filter correctness check
        boolean filterCorrect = true;
        for (UUID propId : retrievedIds) {
            Property prop = propertyRepository.findById(propId).orElse(null);
            if (prop != null && !candidateMerger.passesHardConstraints(prop, filters)) {
                filterCorrect = false;
                break;
            }
        }

        // Determine top contributing channel from stage rankings
        String topChannel = "fused_vector";
        if (!retrievedIds.isEmpty()) {
            UUID topId = retrievedIds.get(0);
            if (!diag.imageChannelRanking().isEmpty() && diag.imageChannelRanking().get(0).equals(topId)) {
                topChannel = "image_vector";
            } else if (!diag.captionChannelRanking().isEmpty() && diag.captionChannelRanking().get(0).equals(topId)) {
                topChannel = "caption_vector";
            } else if (!diag.descriptionChannelRanking().isEmpty() && diag.descriptionChannelRanking().get(0).equals(topId)) {
                topChannel = "description_vector";
            }
        }

        List<StageLatency> latencies = new ArrayList<>();
        if (diag.stageLatenciesMs() != null) {
            diag.stageLatenciesMs().forEach((k, v) -> latencies.add(new StageLatency(k, v)));
        }
        latencies.add(new StageLatency("total_query", duration));

        QueryEvalResult evalResult = new QueryEvalResult(
                q.getId(),
                q.getQueryText() != null ? q.getQueryText() : ("[" + queryType + "] " + q.getReferenceImageKey()),
                ndcg5,
                ndcg10,
                recall10,
                prec5,
                mrr,
                filterCorrect,
                topChannel,
                latencies,
                retrievedIds
        );

        return new SingleQueryEvaluation(
                evalResult, imageNdcg, captionNdcg, descNdcg, fusedNdcg, aggNdcg, mlNdcg, finalNdcg,
                diag.stageLatenciesMs() != null ? diag.stageLatenciesMs() : Map.of()
        );
    }

    private double computeNdcg(List<UUID> retrieved, Map<UUID, Integer> grades, int k) {
        if (retrieved.isEmpty()) return 0.0;
        int limit = Math.min(k, retrieved.size());

        double dcg = 0.0;
        for (int i = 0; i < limit; i++) {
            int grade = grades.getOrDefault(retrieved.get(i), 0);
            dcg += (Math.pow(2, grade) - 1) / (Math.log(i + 2) / Math.log(2));
        }

        List<Integer> allGrades = new ArrayList<>(grades.values());
        if (allGrades.isEmpty()) return 1.0;
        allGrades.sort(Collections.reverseOrder());

        double idcg = 0.0;
        int idcgLimit = Math.min(k, allGrades.size());
        for (int i = 0; i < idcgLimit; i++) {
            idcg += (Math.pow(2, allGrades.get(i)) - 1) / (Math.log(i + 2) / Math.log(2));
        }

        if (idcg == 0.0) return 1.0;
        return dcg / idcg;
    }

    private double computeRecall(List<UUID> retrieved, List<UUID> expected, int k) {
        if (expected == null || expected.isEmpty()) return 1.0;
        int limit = Math.min(k, retrieved.size());
        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (expected.contains(retrieved.get(i))) hits++;
        }
        return (double) hits / expected.size();
    }

    private double computePrecision(List<UUID> retrieved, List<UUID> expected, int k) {
        if (retrieved.isEmpty() || k <= 0) return 0.0;
        int limit = Math.min(k, retrieved.size());
        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (expected.contains(retrieved.get(i))) hits++;
        }
        return (double) hits / k;
    }

    private double computeMrr(List<UUID> retrieved, List<UUID> expected) {
        if (expected == null || expected.isEmpty()) return 1.0;
        for (int i = 0; i < retrieved.size(); i++) {
            if (expected.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private Map<UUID, Integer> parseGrades(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            Map<UUID, Integer> map = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                map.put(UUID.fromString(entry.getKey()), ((Number) entry.getValue()).intValue());
            }
            return map;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<UUID> parseExpectedIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> list = objectMapper.readValue(json, List.class);
            return list.stream().map(UUID::fromString).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<VisionEvalQuery> seedSampleEvalQueries() {
        List<VisionEvalQuery> list = new ArrayList<>();
        list.add(VisionEvalQuery.builder()
                .queryText("modern beachfront villa with private pool")
                .queryType("TEXT_ONLY")
                .city("Santorini")
                .maxPricePerNight(1500.0)
                .minGuests(4)
                .notes("Standard visual search benchmark")
                .build());
        list.add(VisionEvalQuery.builder()
                .queryText("cozy rustic mountain cabin with fireplace")
                .queryType("TEXT_ONLY")
                .city("Aspen")
                .maxPricePerNight(800.0)
                .minGuests(2)
                .notes("Aesthetic niche benchmark")
                .build());
        list.add(VisionEvalQuery.builder()
                .queryText("bright minimalist studio for remote work")
                .queryType("TEXT_ONLY")
                .city("Lisbon")
                .maxPricePerNight(200.0)
                .minGuests(1)
                .notes("Business nomad benchmark")
                .build());
        return evalQueryRepository.saveAll(list);
    }

    private byte[] loadFallbackImageBytes(String imageKey, List<UUID> expectedIds) {
        try {
            int order = 1;
            if (imageKey != null && imageKey.contains("-") && imageKey.endsWith(".jpg")) {
                String sub = imageKey.substring(imageKey.lastIndexOf('-') + 1, imageKey.lastIndexOf('.'));
                try {
                    order = Integer.parseInt(sub);
                } catch (Exception ignored) {}
            }

            String slug = null;
            if (expectedIds != null && !expectedIds.isEmpty()) {
                Property prop = propertyRepository.findById(expectedIds.get(0)).orElse(null);
                if (prop != null) {
                    slug = mapTitleToSlug(prop.getTitle());
                }
            }

            if (slug != null) {
                java.nio.file.Path dir = java.nio.file.Paths.get("data", "ground-truth-photos", slug);
                if (java.nio.file.Files.exists(dir)) {
                    String prefix = String.format("%02d_", order);
                    try (var stream = java.nio.file.Files.list(dir)) {
                        var match = stream.filter(p -> p.getFileName().toString().startsWith(prefix) && p.getFileName().toString().endsWith(".jpg")).findFirst();
                        if (match.isPresent()) {
                            return java.nio.file.Files.readAllBytes(match.get());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Fallback disk image load failed for key {}: {}", imageKey, e.getMessage());
        }
        return null;
    }

    private String mapTitleToSlug(String title) {
        if (title == null) return null;
        String lower = title.toLowerCase();
        if (lower.contains("santorini")) return "01_santorini_villa";
        if (lower.contains("zermatt")) return "02_zermatt_chalet";
        if (lower.contains("manhattan") || lower.contains("sky-penthouse")) return "03_manhattan_penthouse";
        if (lower.contains("bambu") || lower.contains("bali")) return "04_bali_bambu_sanctuary";
        if (lower.contains("copenhagen") || lower.contains("nordic")) return "05_copenhagen_penthouse";
        if (lower.contains("marrakech") || lower.contains("layla")) return "06_marrakech_riad";
        if (lower.contains("ibiza") || lower.contains("conta")) return "07_ibiza_sunset_villa";
        if (lower.contains("kyoto") || lower.contains("machiya")) return "08_kyoto_machiya";
        if (lower.contains("castle") || lower.contains("highland")) return "09_scottish_castle";
        if (lower.contains("tuscany") || lower.contains("vigneto")) return "10_tuscany_farmhouse";
        return null;
    }
}
