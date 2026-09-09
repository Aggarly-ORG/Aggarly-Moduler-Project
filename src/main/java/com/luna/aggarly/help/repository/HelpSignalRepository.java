package com.luna.aggarly.help.repository;

import com.luna.aggarly.help.entity.HelpSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HelpSignalRepository extends JpaRepository<HelpSignal, UUID> {
    List<HelpSignal> findByUserIdOrderByCreatedAtDesc(UUID userId);
}