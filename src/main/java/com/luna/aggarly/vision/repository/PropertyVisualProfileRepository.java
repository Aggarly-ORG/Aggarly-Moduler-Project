package com.luna.aggarly.vision.repository;

import com.luna.aggarly.vision.entity.PropertyVisualProfile;
import com.luna.aggarly.vision.entity.enums.ProfileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PropertyVisualProfileRepository extends JpaRepository<PropertyVisualProfile, UUID> {

    Optional<PropertyVisualProfile> findByPropertyId(UUID propertyId);

    List<PropertyVisualProfile> findByProfileStatus(ProfileStatus profileStatus);

    boolean existsByPropertyId(UUID propertyId);
}
