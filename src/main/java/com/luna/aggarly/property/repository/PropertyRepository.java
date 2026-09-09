package com.luna.aggarly.property.repository;

import com.luna.aggarly.property.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.luna.aggarly.property.entity.enums.PropertyStatus;

import java.util.UUID;

@Repository
public interface PropertyRepository extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {
    
    Page<Property> findByHostId(UUID hostId, Pageable pageable);

    java.util.List<Property> findByHostId(UUID hostId);

    long countByStatus(PropertyStatus status);
}
