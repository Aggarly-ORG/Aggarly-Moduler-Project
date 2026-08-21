package com.luna.aggarly.vision.worker;

import com.luna.aggarly.vision.event.PropertyDeletedEvent;
import com.luna.aggarly.vision.event.PropertyImageDeletedEvent;
import com.luna.aggarly.vision.event.PropertyImageReplacedEvent;
import com.luna.aggarly.vision.event.PropertyImageUploadedEvent;
import com.luna.aggarly.vision.vector.VisionVectorCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyImageEventListener {

    private final VisionTaskQueueService taskQueueService;
    private final VisionVectorCleanupService vectorCleanupService;

    @Async("visionWorkerExecutor")
    @EventListener
    public void onPropertyImageUploaded(PropertyImageUploadedEvent event) {
        log.info("Received PropertyImageUploadedEvent: imageId={}, propertyId={}, isCover={}",
                event.imageId(), event.propertyId(), event.isCover());
        int priority = event.isCover() ? 1 : 3;
        taskQueueService.enqueueImageProcessing(event.imageId(), event.propertyId(), priority);
    }

    @Async("visionWorkerExecutor")
    @EventListener
    public void onPropertyImageReplaced(PropertyImageReplacedEvent event) {
        log.info("Received PropertyImageReplacedEvent: imageId={}, propertyId={}", event.imageId(), event.propertyId());
        vectorCleanupService.removeImagePoint(event.imageId());
        taskQueueService.enqueueReprocess(event.imageId(), event.propertyId());
    }

    @Async("visionWorkerExecutor")
    @EventListener
    public void onPropertyImageDeleted(PropertyImageDeletedEvent event) {
        log.info("Received PropertyImageDeletedEvent: imageId={}, propertyId={}", event.imageId(), event.propertyId());
        vectorCleanupService.removeImagePoint(event.imageId());
        taskQueueService.enqueueProfileAggregation(event.propertyId());
    }

    @Async("visionWorkerExecutor")
    @EventListener
    public void onPropertyDeleted(PropertyDeletedEvent event) {
        log.info("Received PropertyDeletedEvent: propertyId={}", event.propertyId());
        vectorCleanupService.removePropertyPoints(event.propertyId());
    }
}
