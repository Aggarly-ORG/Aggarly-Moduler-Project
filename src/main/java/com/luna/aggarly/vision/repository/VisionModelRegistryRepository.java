package com.luna.aggarly.vision.repository;

import com.luna.aggarly.vision.entity.VisionModelRegistry;
import com.luna.aggarly.vision.entity.enums.VisionModelTaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VisionModelRegistryRepository extends JpaRepository<VisionModelRegistry, UUID> {

    Optional<VisionModelRegistry> findByModelNameAndModelVersion(String modelName, String modelVersion);

    List<VisionModelRegistry> findByTaskTypeAndActiveTrue(VisionModelTaskType taskType);

    Optional<VisionModelRegistry> findFirstByTaskTypeAndActiveTrueOrderByCreatedAtDesc(VisionModelTaskType taskType);
}
