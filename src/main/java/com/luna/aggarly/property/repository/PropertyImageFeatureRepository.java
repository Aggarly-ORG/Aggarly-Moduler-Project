package com.luna.aggarly.property.repository;

import com.luna.aggarly.property.entity.PropertyImageFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PropertyImageFeatureRepository extends JpaRepository<PropertyImageFeature, UUID> {
}
