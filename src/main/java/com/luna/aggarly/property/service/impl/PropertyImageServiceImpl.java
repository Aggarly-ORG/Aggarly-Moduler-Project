package com.luna.aggarly.property.service.impl;

import com.luna.aggarly.filestorage.service.FileStorageService;
import com.luna.aggarly.property.dto.request.AddPropertyImageRequest;
import com.luna.aggarly.property.dto.response.PropertyImageResponse;
import com.luna.aggarly.property.entity.Property;
import com.luna.aggarly.property.entity.PropertyImage;
import com.luna.aggarly.property.event.ImageUploadedEvent;
import com.luna.aggarly.property.exceptions.PropertyNotFoundException;
import com.luna.aggarly.property.exceptions.UnauthorizedPropertyAccessException;
import com.luna.aggarly.property.repository.PropertyImageRepository;
import com.luna.aggarly.property.repository.PropertyRepository;
import com.luna.aggarly.property.service.PropertyImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service implementation for managing property images, ordering, and cover photo state.
 */
@Service
@RequiredArgsConstructor
public class PropertyImageServiceImpl implements PropertyImageService {

    private final PropertyRepository propertyRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher publisher;

    private PropertyImageResponse toResponse(PropertyImage image) {
        return new PropertyImageResponse(
                image.getId(),
                image.getObjectKey(),
                image.getDisplayOrder(),
                image.isCover()
        );
    }

    private Property getPropertyAndVerifyAccess(UUID propertyId, UUID hostId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property not found with ID: " + propertyId));

        if (!property.getHostId().equals(hostId)) {
            throw new UnauthorizedPropertyAccessException("You do not have permission to modify this property's images.");
        }
        return property;
    }

    @Override
    @Transactional
    public PropertyImageResponse addImage(UUID propertyId, UUID hostId, AddPropertyImageRequest request) {
        Property property = getPropertyAndVerifyAccess(propertyId, hostId);
        fileStorageService.markAsActive(request.objectKey());

        int nextOrder = property.getImages().stream()
                .mapToInt(PropertyImage::getDisplayOrder)
                .max()
                .orElse(0) + 1;

        boolean setAsCover = request.isCover() || property.getImages().isEmpty();

        if (setAsCover) {
            property.getImages().forEach(img -> img.setCover(false));
        }

        PropertyImage image = PropertyImage.builder()
                .property(property)
                .objectKey(request.objectKey())
                .displayOrder(nextOrder)
                .isCover(setAsCover)
                .build();

        PropertyImage savedImage = propertyImageRepository.save(image);
        publisher.publishEvent(new ImageUploadedEvent(propertyId, savedImage.getId()));

        return toResponse(savedImage);
    }

    @Override
    @Transactional
    public void deleteImage(UUID propertyId, UUID hostId, UUID imageId) {
        Property property = getPropertyAndVerifyAccess(propertyId, hostId);

        PropertyImage targetImage = propertyImageRepository.findById(imageId)
                .orElseThrow(() -> new PropertyNotFoundException("Image not found with ID: " + imageId));

        if (!targetImage.getProperty().getId().equals(property.getId())) {
            throw new UnauthorizedPropertyAccessException("Image does not belong to this property");
        }

        propertyImageRepository.delete(targetImage);

        if (targetImage.isCover() && property.getImages().size() > 1) {
            PropertyImage nextCover = property.getImages().stream()
                    .filter(img -> !img.getId().equals(imageId))
                    .findFirst()
                    .orElse(null);

            if (nextCover != null) {
                nextCover.setCover(true);
                propertyImageRepository.save(nextCover);
            }
        }
    }

    @Override
    @Transactional
    public PropertyImageResponse setCoverImage(UUID propertyId, UUID hostId, UUID imageId) {
        Property property = getPropertyAndVerifyAccess(propertyId, hostId);

        PropertyImage targetImage = propertyImageRepository.findById(imageId)
                .orElseThrow(() -> new PropertyNotFoundException("Image not found with ID: " + imageId));

        if (!targetImage.getProperty().getId().equals(property.getId())) {
            throw new UnauthorizedPropertyAccessException("Image does not belong to this property");
        }

        property.getImages().forEach(img -> img.setCover(false));
        targetImage.setCover(true);

        return toResponse(propertyImageRepository.save(targetImage));
    }
}
