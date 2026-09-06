package com.luna.aggarly.vision.search;

import com.luna.aggarly.filestorage.service.FileStorageService;
import com.luna.aggarly.vision.pipeline.ImageDomainValidator;
import com.luna.aggarly.vision.search.records.SearchMode;
import com.luna.aggarly.vision.search.records.VisionSearchFilters;
import com.luna.aggarly.vision.search.records.VisionSearchQuery;
import com.luna.aggarly.vision.search.records.VisionSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageSimilaritySearchService {

    private final VisionSearchOrchestrator searchOrchestrator;
    private final FileStorageService fileStorageService;
    private final ImageDomainValidator imageDomainValidator;

    public List<VisionSearchResult> findSimilarByImageKey(String referenceObjectKey, String textRefinement, VisionSearchFilters filters, int pageSize) {
        log.info("Image similarity search for objectKey={}, textRefinement={}", referenceObjectKey, textRefinement);
        byte[] imageBytes = new byte[0];
        try (InputStream is = fileStorageService.getFileStream(referenceObjectKey)) {
            if (is != null) {
                imageBytes = is.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("Could not read reference image stream from storage for key: {}", referenceObjectKey);
        }

        // 1. Validate that the uploaded search image is within real estate / property domain
        if (imageBytes.length > 0) {
            imageDomainValidator.validateOrThrow(imageBytes);
        }

        SearchMode mode = (textRefinement != null && !textRefinement.isBlank()) ? SearchMode.MULTIMODAL : SearchMode.IMAGE_ONLY;
        float minScore = (mode == SearchMode.IMAGE_ONLY) ? 0.38f : 0.25f;

        VisionSearchQuery query = new VisionSearchQuery(
                textRefinement,
                imageBytes,
                referenceObjectKey,
                mode == SearchMode.MULTIMODAL ? 0.60f : 1.0f,
                mode,
                pageSize > 0 ? pageSize : 10,
                null,
                minScore,
                filters != null ? filters : VisionSearchFilters.empty()
        );

        return searchOrchestrator.search(query);
    }
}
