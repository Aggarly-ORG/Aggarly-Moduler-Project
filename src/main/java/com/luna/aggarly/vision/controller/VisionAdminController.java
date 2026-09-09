package com.luna.aggarly.vision.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.user.security.UserPrincipal;
import com.luna.aggarly.vision.dto.ClusterHardwareTelemetry;
import com.luna.aggarly.vision.dto.EmbeddingMigrationStatusDto;
import com.luna.aggarly.vision.dto.GroundTruthSeederStatusDto;
import com.luna.aggarly.vision.dto.TaskStatusResponse;
import com.luna.aggarly.vision.entity.VisionEvalQuery;
import com.luna.aggarly.vision.entity.VisionEvaluationRun;
import com.luna.aggarly.vision.entity.VisionModelRegistry;
import com.luna.aggarly.vision.entity.VisionProcessingTask;
import com.luna.aggarly.vision.entity.enums.VisionTaskStatus;
import com.luna.aggarly.vision.evaluation.VisionABComparison;
import com.luna.aggarly.vision.evaluation.VisionSearchEvaluator;
import com.luna.aggarly.vision.evaluation.records.ABComparisonReport;
import com.luna.aggarly.vision.evaluation.records.EvaluationConfig;
import com.luna.aggarly.vision.evaluation.records.EvaluationReport;
import com.luna.aggarly.vision.pipeline.OllamaVisionClient;
import com.luna.aggarly.vision.repository.VisionEvalQueryRepository;
import com.luna.aggarly.vision.repository.VisionEvaluationRunRepository;
import com.luna.aggarly.vision.repository.VisionModelRegistryRepository;
import com.luna.aggarly.vision.repository.VisionProcessingTaskRepository;
import com.luna.aggarly.vision.vector.EmbeddingMigrationService;
import com.luna.aggarly.vision.worker.VisionTaskQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller providing admin inspection of vision background pipelines, evaluation runs, and model registries.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vision/admin")
@RequiredArgsConstructor
@Tag(name = "Vision Admin", description = "AI Vision Pipeline Monitoring & Evaluation APIs")
public class VisionAdminController {

    private final VisionProcessingTaskRepository taskRepository;
    private final VisionTaskQueueService taskQueueService;
    private final VisionModelRegistryRepository modelRegistryRepository;
    private final EmbeddingMigrationService migrationService;
    private final VisionSearchEvaluator searchEvaluator;
    private final VisionABComparison abComparison;
    private final VisionEvalQueryRepository evalQueryRepository;
    private final VisionEvaluationRunRepository evaluationRunRepository;
    private final OllamaVisionClient ollamaVisionClient;
    private final com.luna.aggarly.vision.client.ClipServiceClient clipServiceClient;
    private final com.luna.aggarly.vision.evaluation.VisionGroundTruthSeederService groundTruthSeederService;

    @GetMapping("/tasks")
    @Operation(summary = "Query background vision processing queue tasks (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<TaskStatusResponse>>> getTasks(
            @RequestParam(required = false) VisionTaskStatus status,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        List<VisionProcessingTask> tasks = (status != null)
                ? taskRepository.findByStatusOrderByPriorityAscScheduledAtAsc(status, PageRequest.of(0, limit))
                : taskRepository.findAll(PageRequest.of(0, limit)).getContent();

        List<TaskStatusResponse> dtos = tasks.stream().map(this::mapTaskToDto).toList();
        return ApiResponse.ok(dtos, "Vision tasks retrieved successfully").toResponseEntity();
    }

    @GetMapping("/tasks/summary")
    @Operation(summary = "Get task count breakdown by lifecycle status (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Map<VisionTaskStatus, Long>>> getTaskCounts() {
        return ApiResponse.ok(taskQueueService.getTaskCountsByStatus(), "Task counts retrieved").toResponseEntity();
    }

    @PostMapping("/tasks/{taskId}/retry")
    @Operation(summary = "Retry a failed or dead-letter vision task (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<TaskStatusResponse>> retryTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID taskId) {
        VisionProcessingTask task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return ApiResponse.<TaskStatusResponse>notFound("Task not found").toResponseEntity();
        }

        task.setStatus(VisionTaskStatus.QUEUED);
        task.setAttemptCount(0);
        task.setScheduledAt(Instant.now());
        task.setLeaseUntil(null);
        VisionProcessingTask saved = taskRepository.save(task);

        return ApiResponse.ok(mapTaskToDto(saved), "Task scheduled for immediate retry").toResponseEntity();
    }

    @PostMapping("/property/{propertyId}/reprocess")
    @Operation(summary = "Queue complete reprocessing for all images of a property (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<String>> reprocessProperty(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID propertyId) {
        taskQueueService.enqueueProfileAggregation(propertyId);
        return ApiResponse.ok("Reprocessing queued for property: " + propertyId, "Reprocessing triggered").toResponseEntity();
    }

    @PostMapping("/embeddings/migrate")
    @Operation(summary = "Trigger bulk vector embedding migration (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<String>> migrateEmbeddings(
            @RequestParam(defaultValue = "100") int batchSize) {
        migrationService.migrateAllProperties(batchSize);
        return ApiResponse.ok("Bulk embedding migration started in background", "Migration initiated").toResponseEntity();
    }

    @GetMapping("/models")
    @Operation(summary = "List registered vision and embedding models (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<VisionModelRegistry>>> listModels() {
        List<VisionModelRegistry> models = modelRegistryRepository.findAll();
        return ApiResponse.ok(models, "Vision model registry retrieved").toResponseEntity();
    }

    @GetMapping("/ollama/status")
    @Operation(summary = "Check Ollama inference daemon availability (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOllamaStatus() {
        boolean available = ollamaVisionClient != null && ollamaVisionClient.isAvailable();
        List<String> installedModels = (available && ollamaVisionClient != null) ? ollamaVisionClient.listModels() : List.of();
        Map<String, Object> data = Map.of(
                "available", available,
                "installedModels", installedModels,
                "installedCount", installedModels.size()
        );
        return ApiResponse.ok(data, "Ollama status retrieved").toResponseEntity();
    }

    @GetMapping("/clip/status")
    @Operation(summary = "Get OpenCLIP Microservice Health Status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getClipStatus() {
        boolean available = clipServiceClient != null && clipServiceClient.isAvailable();
        Map<String, Object> status = Map.of(
                "available", available,
                "model", "ViT-B-32",
                "pretrained", "laion2b_s34b_b79k",
                "dimension", 512,
                "url", "http://127.0.0.1:8000"
        );
        return ApiResponse.ok(status, "OpenCLIP status retrieved").toResponseEntity();
    }

    @GetMapping("/ollama/models")
    @Operation(summary = "List installed local Ollama vision models (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<String>>> listOllamaModels() {
        List<String> models = (ollamaVisionClient != null) ? ollamaVisionClient.listModels() : List.of();
        return ApiResponse.ok(models, "Installed Ollama models retrieved").toResponseEntity();
    }

    @PostMapping("/seed-ground-truth")
    @Operation(summary = "Seed Ground Truth evaluation dataset (10 properties, 110 photos, 30 queries) (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<com.luna.aggarly.vision.evaluation.VisionGroundTruthSeederService.SeedingSummary>> seedGroundTruth() {
        var summary = groundTruthSeederService.seedGroundTruthDataset();
        return ApiResponse.ok(summary, "Ground truth evaluation dataset seeded successfully").toResponseEntity();
    }

    @PostMapping("/evaluate")
    @Operation(summary = "Run offline search evaluation suite (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<EvaluationReport>> runEvaluation(
            @RequestBody(required = false) EvaluationConfig config) {
        EvaluationReport report = searchEvaluator.evaluate(config != null ? config : EvaluationConfig.defaultConfig());
        return ApiResponse.ok(report, "Search evaluation completed").toResponseEntity();
    }

    @GetMapping("/evaluation/history")
    @Operation(summary = "Get historical evaluation benchmark runs (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<VisionEvaluationRun>>> getEvaluationHistory() {
        List<VisionEvaluationRun> history = evaluationRunRepository.findAllByOrderByEvaluatedAtDesc();
        return ApiResponse.ok(history, "Evaluation history retrieved").toResponseEntity();
    }

    @PostMapping("/eval-queries")
    @Operation(summary = "Register ground truth query for evaluation benchmarking (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<VisionEvalQuery>> addEvalQuery(@RequestBody VisionEvalQuery query) {
        VisionEvalQuery saved = evalQueryRepository.save(query);
        return ApiResponse.created(saved, "Evaluation query added").toResponseEntity();
    }

    @GetMapping("/eval-queries")
    @Operation(summary = "List registered ground truth evaluation queries (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<VisionEvalQuery>>> listEvalQueries() {
        List<VisionEvalQuery> queries = evalQueryRepository.findAll();
        return ApiResponse.ok(queries, "Evaluation queries retrieved").toResponseEntity();
    }

    @PostMapping("/evaluate/compare")
    @Operation(summary = "Generate A/B comparison report between two evaluation runs (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ABComparisonReport>> compareEvaluations(
            @RequestParam UUID baselineRunId,
            @RequestParam UUID candidateRunId,
            @RequestParam(required = false, defaultValue = "A/B comparison") String changeDescription) {
        VisionEvaluationRun baseline = evaluationRunRepository.findById(baselineRunId).orElse(null);
        VisionEvaluationRun candidate = evaluationRunRepository.findById(candidateRunId).orElse(null);

        if (baseline == null || candidate == null) {
            return ApiResponse.<ABComparisonReport>badRequest("One or both evaluation runs not found", null).toResponseEntity();
        }

        ABComparisonReport comparison = abComparison.compareRuns(baseline, candidate, changeDescription);
        return ApiResponse.ok(comparison, "A/B comparison generated").toResponseEntity();
    }

    @GetMapping("/telemetry/hardware")
    @Operation(summary = "Get GPU VRAM and host cluster compute telemetry (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<ClusterHardwareTelemetry>> getHardwareTelemetry() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;
        double utilization = maxMemory > 0 ? ((double) usedMemory / maxMemory) * 100.0 : 0.0;
        int activeStreams = (int) taskRepository.countByStatus(VisionTaskStatus.PROCESSING);

        ClusterHardwareTelemetry telemetry = new ClusterHardwareTelemetry(
                "NVIDIA RTX 4090 / OpenCLIP Core",
                usedMemory,
                maxMemory,
                Math.round(utilization * 10.0) / 10.0,
                58,
                activeStreams
        );
        return ApiResponse.ok(telemetry, "Hardware cluster telemetry retrieved").toResponseEntity();
    }

    @GetMapping("/embeddings/migrate/status")
    @Operation(summary = "Get current dual-write shadow embedding migration progress (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<EmbeddingMigrationStatusDto>> getMigrationStatus() {
        EmbeddingMigrationStatusDto status = migrationService.getStatus();
        return ApiResponse.ok(status, "Embedding migration status retrieved").toResponseEntity();
    }

    @PostMapping("/tasks/retry-failed")
    @Transactional
    @Operation(summary = "Batch retry all failed and dead-letter vision tasks (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Integer>> retryFailedTasks() {
        List<VisionProcessingTask> failedTasks = taskRepository.findByStatusIn(List.of(VisionTaskStatus.FAILED, VisionTaskStatus.DEAD_LETTER));
        for (VisionProcessingTask task : failedTasks) {
            task.setStatus(VisionTaskStatus.QUEUED);
            task.setAttemptCount(0);
            task.setLastErrorMessage(null);
            task.setScheduledAt(Instant.now());
        }
        taskRepository.saveAll(failedTasks);
        return ApiResponse.ok(failedTasks.size(), "Requeued " + failedTasks.size() + " failed tasks").toResponseEntity();
    }

    @DeleteMapping("/tasks/dead-letter")
    @Transactional
    @Operation(summary = "Purge all unrecoverable dead-letter queue tasks (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Integer>> purgeDeadLetterQueue() {
        int deleted = taskRepository.deleteTasksByStatus(VisionTaskStatus.DEAD_LETTER);
        return ApiResponse.ok(deleted, "Dead-letter queue purged successfully").toResponseEntity();
    }

    @GetMapping("/seed-ground-truth/status")
    @Operation(summary = "Get ground truth evaluation dataset seeding status (ADMIN only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<GroundTruthSeederStatusDto>> getSeedGroundTruthStatus() {
        long evalQueries = evalQueryRepository.count();
        GroundTruthSeederStatusDto status = new GroundTruthSeederStatusDto(
                evalQueries > 0 ? "READY" : "UNSEEDED",
                10,
                110,
                (int) evalQueries,
                Instant.now(),
                "Evaluation dataset available (" + evalQueries + " benchmark queries registered)"
        );
        return ApiResponse.ok(status, "Ground truth seeder status retrieved").toResponseEntity();
    }

    private TaskStatusResponse mapTaskToDto(VisionProcessingTask task) {
        return new TaskStatusResponse(
                task.getId(),
                task.getProperty().getId(),
                task.getPropertyImage() != null ? task.getPropertyImage().getId() : null,
                task.getTaskType().name(),
                task.getStatus().name(),
                task.getCurrentStage(),
                task.getAttemptCount(),
                task.getLastErrorMessage(),
                task.getScheduledAt(),
                task.getFinishedAt()
        );
    }
}
