package com.luna.aggarly.filestorage.repository;

import com.luna.aggarly.filestorage.entity.StoredFile;
import com.luna.aggarly.filestorage.entity.enums.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    Optional<StoredFile> findByObjectKey(String objectKey);
    List<StoredFile> findByStatusAndCreatedAtBefore(FileStatus status, Instant dateTime);
}
