package com.luna.aggarly.filestorage.service.impl;

import com.luna.aggarly.filestorage.dto.response.DownloadUrlResponse;
import com.luna.aggarly.filestorage.dto.response.StoredFileResponse;
import com.luna.aggarly.filestorage.dto.response.UploadResponse;
import com.luna.aggarly.filestorage.entity.StoredFile;
import com.luna.aggarly.filestorage.entity.enums.FileStatus;
import com.luna.aggarly.filestorage.exceptions.FileAlreadyDeletedException;
import com.luna.aggarly.filestorage.exceptions.FileNotFoundException;
import com.luna.aggarly.filestorage.exceptions.FileStorageException;
import com.luna.aggarly.filestorage.exceptions.StorageUnavailableException;
import com.luna.aggarly.filestorage.mapper.StoredFileMapper;
import com.luna.aggarly.filestorage.repository.StoredFileRepository;
import com.luna.aggarly.filestorage.service.FileStorageService;
import com.luna.aggarly.filestorage.validation.FileValidator;
import com.luna.aggarly.common.security.SecurityUtils;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStorageService implements FileStorageService {

    @Value("${app.storage.bucket-name}")
    private String bucket;
    @Value("${app.storage.presigned-expiration}")
    private long presignedExpiration;

    private final MinioClient minioClient;
    private final FileValidator fileValidator;
    private final StoredFileRepository storedFileRepository;
    private final StoredFileMapper storedFileMapper;

    @Override
    @Transactional
    public UploadResponse uploadFile(MultipartFile file) {
        fileValidator.validate(file);

        UUID uploaderId = SecurityUtils.getCurrentUserId();

        ensureBucketExists();

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
        String extension = getExtension(originalFilename);
        String objectKey = generateObjectKey(extension);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            StoredFile storedFile = StoredFile.builder()
                    .objectKey(objectKey)
                    .bucket(bucket)
                    .originalFilename(originalFilename)
                    .contentType(file.getContentType())
                    .extension(extension)
                    .size(file.getSize())
                    .status(FileStatus.PENDING)
                    .ownerId(uploaderId)
                    .build();

            StoredFile savedFile = storedFileRepository.save(storedFile);
            return storedFileMapper.toUploadResponse(savedFile);

        } catch (Exception e) {
            log.error("Failed to upload file to MinIO", e);
            throw new FileStorageException("Failed to upload file", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadUrlResponse getDownloadUrl(UUID id) {
        StoredFile storedFile = findFile(id);

        try {
            log.info(bucket);
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(storedFile.getBucket())
                            .object(storedFile.getObjectKey())
                            .expiry((int) presignedExpiration, TimeUnit.MINUTES)
                            .build()
            );

            Instant expiresAt = Instant.now().plus(presignedExpiration, java.time.temporal.ChronoUnit.MINUTES);
            return new DownloadUrlResponse(url, expiresAt);

        } catch (Exception e) {
            log.error("Failed to generate presigned URL", e);
            throw new FileStorageException("Failed to generate download URL", e);
        }
    }

    @Override
    @Transactional
    public void deleteFile(UUID id) {
        StoredFile storedFile = storedFileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found with ID: " + id));
                
        if (storedFile.getStatus() == FileStatus.DELETED) {
            throw new FileAlreadyDeletedException("File is already deleted.");
        }

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(storedFile.getBucket())
                            .object(storedFile.getObjectKey())
                            .build()
            );
            
            // Soft delete trigger will be executed by SQLDelete annotation 
            // when we call repository.delete()
            storedFileRepository.delete(storedFile);
            
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO", e);
            throw new FileStorageException("Failed to delete file", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFileResponse getMetadata(UUID id) {
        return storedFileMapper.toResponse(findFile(id));
    }

    @Override
    @Transactional
    public void markAsActive(UUID id) {
        StoredFile storedFile = storedFileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found for key: " + id));
                
        if (storedFile.getStatus() == FileStatus.PENDING) {
            storedFile.setStatus(FileStatus.ACTIVE);
            storedFile.setActivatedAt(Instant.now());
            storedFileRepository.save(storedFile);
        }
    }

    @Override
    public void markAsActive(String objectKey) {
        StoredFile storedFile = storedFileRepository.findByObjectKey(objectKey)
                .orElseThrow(() -> new FileNotFoundException("File not found for key: " + objectKey));

        if (storedFile.getStatus() == FileStatus.PENDING) {
            storedFile.setStatus(FileStatus.ACTIVE);
            storedFile.setActivatedAt(Instant.now());
            storedFileRepository.save(storedFile);
        }
    }

    @Override
    public InputStream getFileStream(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to retrieve file stream from MinIO for key: {}", objectKey, e);
            throw new FileNotFoundException("File not found for key: " + objectKey);
        }
    }

    @Override
    public String getContentType(String objectKey) {
        return storedFileRepository.findByObjectKey(objectKey)
                .map(StoredFile::getContentType)
                .orElse("image/jpeg");
    }

    @Override
    public String resolveUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey;
        }
        return "http://localhost:8081/api/v1/storage/files/view?key=" + objectKey;
    }

    private StoredFile findFile(UUID id) {
        return storedFileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found with ID: " + id));
    }

    private String generateObjectKey(String extension) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneId.systemDefault());
        String prefix = formatter.format(Instant.now());
        String uuid = UUID.randomUUID().toString();
        
        return "files/" + prefix + "/" + uuid + (extension.isEmpty() ? "" : "." + extension);
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }

    private void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.error("Failed to check or create bucket", e);
            throw new StorageUnavailableException("Storage system is unavailable", e);
        }
    }
}
