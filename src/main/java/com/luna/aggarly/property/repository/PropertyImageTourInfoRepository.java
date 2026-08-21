package com.luna.aggarly.property.repository;

import com.luna.aggarly.property.entity.PropertyImageTourInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PropertyImageTourInfoRepository extends JpaRepository<PropertyImageTourInfo, UUID> {
}
