package com.luna.aggarly.watchdog.repository;

import com.luna.aggarly.watchdog.entity.UserWatchdog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserWatchdogRepository extends JpaRepository<UserWatchdog, UUID> {
    List<UserWatchdog> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<UserWatchdog> findByIdAndUserId(UUID id, UUID userId);
    List<UserWatchdog> findByIsActiveTrue();
    List<UserWatchdog> findByPropertyIdAndIsActiveTrue(UUID propertyId);
}