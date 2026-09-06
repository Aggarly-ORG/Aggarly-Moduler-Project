package com.luna.aggarly.property.repository;

import com.luna.aggarly.property.entity.PropertyImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PropertyImageRepository extends JpaRepository<PropertyImage, UUID> {

    List<PropertyImage> findByPropertyId(UUID propertyId);

    List<PropertyImage> findByPropertyIdOrderByDisplayOrderAsc(UUID propertyId);
}
