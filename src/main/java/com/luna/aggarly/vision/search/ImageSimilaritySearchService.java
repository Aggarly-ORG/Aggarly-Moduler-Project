package com.luna.aggarly.vision.search;

import com.luna.aggarly.filestorage.service.FileStorageService;
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

    public List<VisionSearchResult> findSimilarByImageKey(String referenceObjectKey, String textRefinement, VisionSearchFilters filters, int pageSize) {
        log.info("Image similarity search for objectKey={}, textRefinement={}", referenceObjectKey, textRefinement);
        byte[] imageBytes = new byte[0];
        try (InputStream is = fileStorageService.getFileStream(referenceObjectKey)) {
            if (is != null) {
                imageBytes = is.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("Could not read reference image stream from MinIO for key: {}", referenceObjectKey);
        }

        SearchMode mode = (textRefinement != null && !textRefinement.isBlank()) ? SearchMode.MULTIMODAL : SearchMode.IMAGE_ONLY;
        VisionSearchQuery query = new VisionSearchQuery(
                textRefinement,
                imageBytes,
                referenceObjectKey,
                mode == SearchMode.MULTIMODAL ? 0.60f : 1.0f,
                mode,
                pageSize > 0 ? pageSize : 10,
                null,
                0.25f,
                filters != null ? filters : VisionSearchFilters.empty()
        );

        return searchOrchestrator.search(query);
    }
}
