package com.luna.aggarly.vision.repository;

import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.entity.enums.VisionProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyImageAiMetadataRepository extends JpaRepository<PropertyImageAiMetadata, UUID> {

    Optional<PropertyImageAiMetadata> findByPropertyImageId(UUID propertyImageId);

    @Query("SELECT m FROM PropertyImageAiMetadata m LEFT JOIN FETCH m.propertyImage LEFT JOIN FETCH m.property WHERE m.propertyImage.id = :propertyImageId")
    Optional<PropertyImageAiMetadata> findByPropertyImageIdWithDetails(@Param("propertyImageId") UUID propertyImageId);

    @Query("SELECT m FROM PropertyImageAiMetadata m LEFT JOIN FETCH m.propertyImage LEFT JOIN FETCH m.property")
    List<PropertyImageAiMetadata> findAllWithImages();

    List<PropertyImageAiMetadata> findByPropertyId(UUID propertyId);

    List<PropertyImageAiMetadata> findByPropertyIdAndProcessingStatus(UUID propertyId, VisionProcessingStatus status);

    List<PropertyImageAiMetadata> findByProcessingStatus(VisionProcessingStatus status);

    Optional<PropertyImageAiMetadata> findFirstByPerceptualHash(String perceptualHash);

    List<PropertyImageAiMetadata> findByPerceptualHash(String perceptualHash);

    long countByPropertyIdAndProcessingStatus(UUID propertyId, VisionProcessingStatus status);

    long countByPropertyId(UUID propertyId);

    @Query("SELECT m FROM PropertyImageAiMetadata m WHERE m.property.id = :propertyId AND m.moderationStatus = 'APPROVED' AND m.processingStatus = 'COMPLETED'")
    List<PropertyImageAiMetadata> findUsableByPropertyId(@Param("propertyId") UUID propertyId);

    long countByQdrantPointIdIsNotNull();
}
