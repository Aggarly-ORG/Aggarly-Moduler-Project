package com.luna.aggarly.vision.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.vision.dto.VisionImageSearchRequest;
import com.luna.aggarly.vision.dto.VisionMultimodalSearchRequest;
import com.luna.aggarly.vision.dto.VisionSearchResponse;
import com.luna.aggarly.vision.dto.VisionTextSearchRequest;
import com.luna.aggarly.vision.search.ImageSimilaritySearchService;
import com.luna.aggarly.vision.search.VisionSearchOrchestrator;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller providing multi-modal, image similarity, and semantic text property search endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vision/search")
@RequiredArgsConstructor
@Tag(name = "Vision Search", description = "AI Visual & Multimodal Search APIs")
public class VisionSearchController {

    private final VisionSearchOrchestrator searchOrchestrator;
    private final ImageSimilaritySearchService imageSearchService;

    @PostMapping("/text")
    @Operation(summary = "Semantic visual property search via natural language text query (Public)")
    public ResponseEntity<ApiResponse<VisionSearchResponse>> searchByText(
            @Valid @RequestBody VisionTextSearchRequest request) {
        VisionSearchQuery query = VisionSearchQuery.textQuery(
                request.query(),
                request.toFilters(),
                request.pageSize() != null ? request.pageSize() : 10,
                request.cursor(),
                request.minScore()
        );

        List<VisionSearchResult> results = searchOrchestrator.search(query);
        VisionSearchResponse response = new VisionSearchResponse(results, results.size(), null);
        return ApiResponse.ok(response, "Visual search completed successfully").toResponseEntity();
    }

    @PostMapping("/image")
    @Operation(summary = "Image similarity property search via reference image object key (Public)")
    public ResponseEntity<ApiResponse<VisionSearchResponse>> searchByImage(
            @Valid @RequestBody VisionImageSearchRequest request) {
        List<VisionSearchResult> results = imageSearchService.findSimilarByImageKey(
                request.referenceObjectKey(),
                null,
                request.toFilters(),
                request.pageSize() != null ? request.pageSize() : 10
        );

        VisionSearchResponse response = new VisionSearchResponse(results, results.size(), null);
        return ApiResponse.ok(response, "Image similarity search completed").toResponseEntity();
    }

    @PostMapping("/multimodal")
    @Operation(summary = "Multimodal visual search combining reference image and text constraints (Public)")
    public ResponseEntity<ApiResponse<VisionSearchResponse>> searchMultimodal(
            @Valid @RequestBody VisionMultimodalSearchRequest request) {
        List<VisionSearchResult> results = imageSearchService.findSimilarByImageKey(
                request.referenceObjectKey(),
                request.textQuery(),
                request.toFilters(),
                request.pageSize() != null ? request.pageSize() : 10
        );

        VisionSearchResponse response = new VisionSearchResponse(results, results.size(), null);
        return ApiResponse.ok(response, "Multimodal visual search completed").toResponseEntity();
    }

    @GetMapping("/similar/{propertyId}")
    @Operation(summary = "Find visually similar properties to an existing listing (Public)")
    public ResponseEntity<ApiResponse<VisionSearchResponse>> searchSimilarToProperty(
            @PathVariable UUID propertyId,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {
        VisionSearchQuery query = VisionSearchQuery.textQuery(
                "Visually similar luxury property",
                null,
                pageSize,
                null
        );

        List<VisionSearchResult> results = searchOrchestrator.search(query).stream()
                .filter(r -> !r.propertyId().equals(propertyId))
                .toList();

        VisionSearchResponse response = new VisionSearchResponse(results, results.size(), null);
        return ApiResponse.ok(response, "Similar properties retrieved").toResponseEntity();
    }
}
