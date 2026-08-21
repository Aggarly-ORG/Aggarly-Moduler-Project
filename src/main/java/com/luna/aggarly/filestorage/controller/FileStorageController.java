package com.luna.aggarly.filestorage.controller;

import com.luna.aggarly.common.dto.ApiResponse;
import com.luna.aggarly.filestorage.dto.response.DownloadUrlResponse;
import com.luna.aggarly.filestorage.dto.response.StoredFileResponse;
import com.luna.aggarly.filestorage.dto.response.UploadResponse;
import com.luna.aggarly.filestorage.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * Controller managing file uploads, metadata queries, presigned URL generation, and binary file streaming.
 */
@RestController
@RequestMapping("/api/v1/storage/files")
@RequiredArgsConstructor
@Tag(name = "File Storage", description = "Manage file uploads and downloads (Provider independent)")
public class FileStorageController {

    private final FileStorageService fileStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file (Returns object key for use in business modules)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file) {
        UploadResponse response = fileStorageService.uploadFile(file);
        return ApiResponse.created(response, "File uploaded successfully").toResponseEntity();
    }

    @PostMapping("/activate-file/{id}")
    @Operation(summary = "Mark an uploaded file active", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> activateFile(@PathVariable UUID id) {
        fileStorageService.markAsActive(id);
        return ApiResponse.<Void>empty("File activated successfully").toResponseEntity();
    }

    @GetMapping("/view")
    @Operation(summary = "View and stream a stored file by object key (Public)")
    public ResponseEntity<InputStreamResource> viewFile(@RequestParam("key") String objectKey) {
        InputStream stream = fileStorageService.getFileStream(objectKey);
        String contentType = fileStorageService.getContentType(objectKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/{id}/url")
    @Operation(summary = "Get a presigned download URL for a file")
    public ResponseEntity<ApiResponse<DownloadUrlResponse>> getDownloadUrl(@PathVariable UUID id) {
        DownloadUrlResponse response = fileStorageService.getDownloadUrl(id);
        return ApiResponse.ok(response, "Presigned URL generated").toResponseEntity();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get metadata of a stored file", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<StoredFileResponse>> getMetadata(@PathVariable UUID id) {
        StoredFileResponse response = fileStorageService.getMetadata(id);
        return ApiResponse.ok(response, "File metadata retrieved").toResponseEntity();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete a stored file", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable UUID id) {
        fileStorageService.deleteFile(id);
        return ApiResponse.<Void>empty("File deleted successfully").toResponseEntity();
    }
}
