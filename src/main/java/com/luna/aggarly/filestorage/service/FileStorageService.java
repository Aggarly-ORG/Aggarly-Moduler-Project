package com.luna.aggarly.filestorage.service;

import com.luna.aggarly.filestorage.dto.response.DownloadUrlResponse;
import com.luna.aggarly.filestorage.dto.response.StoredFileResponse;
import com.luna.aggarly.filestorage.dto.response.UploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface FileStorageService {

    UploadResponse uploadFile(MultipartFile file);

    DownloadUrlResponse getDownloadUrl(UUID id);

    void deleteFile(UUID id);

    StoredFileResponse getMetadata(UUID id);

    void markAsActive(UUID id);
    void markAsActive(String objectKey);

    java.io.InputStream getFileStream(String objectKey);

    String getContentType(String objectKey);

    String resolveUrl(String objectKey);
}
