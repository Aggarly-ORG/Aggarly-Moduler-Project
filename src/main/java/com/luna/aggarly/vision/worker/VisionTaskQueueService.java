package com.luna.aggarly.vision.worker;

import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.PropertyImage;
import com.luna.aggarly.property.repository.PropertyImageRepository;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.vision.entity.VisionProcessingTask;
import com.luna.aggarly.vision.entity.enums.VisionTaskStatus;
import com.luna.aggarly.vision.entity.enums.VisionTaskType;
import com.luna.aggarly.vision.repository.VisionProcessingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionTaskQueueService {

    private final VisionProcessingTaskRepository taskRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyImageRepository propertyImageRepository;

    @Transactional
    public VisionProcessingTask enqueueImageProcessing(UUID imageId, UUID propertyId, int priority) {
        log.info("Enqueuing image processing task: imageId={}, propertyId={}, priority={}", imageId, propertyId, priority);

        // Check idempotency
        List<VisionProcessingTask> active = taskRepository.findByPropertyImageIdAndStatusIn(
                imageId, List.of(VisionTaskStatus.QUEUED, VisionTaskStatus.PROCESSING)
        );
        if (!active.isEmpty()) {
            log.info("Active task already exists for imageId={}, returning existing task", imageId);
            return active.get(0);
        }

        Property property = propertyRepository.findById(propertyId).orElse(null);
        PropertyImage image = propertyImageRepository.findById(imageId).orElse(null);

        if (property == null || image == null) {
            log.warn("Cannot enqueue task: Property or PropertyImage not found");
            return null;
        }

        VisionProcessingTask task = VisionProcessingTask.builder()
                .property(property)
                .propertyImage(image)
                .taskType(VisionTaskType.PROCESS_IMAGE)
                .status(VisionTaskStatus.QUEUED)
                .priority(priority)
                .attemptCount(0)
                .maxAttempts(3)
                .scheduledAt(Instant.now())
                .build();

        return taskRepository.save(task);
    }

    @Transactional
    public VisionProcessingTask enqueueReprocess(UUID imageId, UUID propertyId) {
        return enqueueImageProcessing(imageId, propertyId, 5);
    }

    @Transactional
    public VisionProcessingTask enqueueProfileAggregation(UUID propertyId) {
        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) return null;

        VisionProcessingTask task = VisionProcessingTask.builder()
                .property(property)
                .taskType(VisionTaskType.GENERATE_PROPERTY_PROFILE)
                .status(VisionTaskStatus.QUEUED)
                .priority(8)
                .attemptCount(0)
                .maxAttempts(3)
                .scheduledAt(Instant.now())
                .build();

        return taskRepository.save(task);
    }

    @Transactional
    public void renewLease(UUID taskId, String workerInstanceId, int leaseSeconds) {
        Instant newLease = Instant.now().plusSeconds(leaseSeconds);
        taskRepository.renewLease(taskId, workerInstanceId, newLease, Instant.now());
    }

    public Map<VisionTaskStatus, Long> getTaskCountsByStatus() {
        Map<VisionTaskStatus, Long> counts = new HashMap<>();
        for (VisionTaskStatus status : VisionTaskStatus.values()) {
            counts.put(status, taskRepository.countByStatus(status));
        }
        return counts;
    }
}
