package com.luna.aggarly.cleaning.repository;

import com.luna.aggarly.cleaning.entity.CleaningPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CleaningPhotoRepository extends JpaRepository<CleaningPhoto, UUID> {

    List<CleaningPhoto> findByCleaningTaskId(UUID cleaningTaskId);
}
