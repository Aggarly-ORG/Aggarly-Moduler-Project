package com.luna.aggarly.vision.listener;

import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.PropertyImage;
import com.luna.aggarly.property.event.ImageUploadedEvent;
import com.luna.aggarly.property.repository.PropertyImageRepository;
import com.luna.aggarly.vision.entity.VisionProcessingTask;
import com.luna.aggarly.vision.entity.enums.VisionTaskStatus;
import com.luna.aggarly.vision.entity.enums.VisionTaskType;
import com.luna.aggarly.vision.repository.VisionProcessingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageUploadedEventListener {

    private final PropertyImageRepository propertyImageRepository;
    private final VisionProcessingTaskRepository visionProcessingTaskRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onImageUploaded(ImageUploadedEvent event) {

        log.info(
                "Creating vision processing task for property image {}",
                event.propertyImageId()
        );

        PropertyImage propertyImage = propertyImageRepository
                .findById(event.propertyImageId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Property image not found: " + event.propertyImageId()
                        )
                );

        Property property = propertyImage.getProperty();
        log.info("property id: {}",property.getId());
        if (property == null) {
            throw new IllegalStateException(
                    "Property image has no associated property: "
                            + event.propertyImageId()
            );
        }

        VisionProcessingTask task = VisionProcessingTask.builder()
                .property(property)
                .propertyImage(propertyImage)
                .taskType(VisionTaskType.PROCESS_IMAGE)
                .status(VisionTaskStatus.QUEUED)
                .priority(5)
                .attemptCount(0)
                .maxAttempts(3)
                .scheduledAt(java.time.Instant.now())
                .createdAt(java.time.Instant.now())
                .build();

        visionProcessingTaskRepository.save(task);

        log.info(
                "Vision processing task {} created for image {}",
                task.getId(),
                event.propertyImageId()
        );
    }
}