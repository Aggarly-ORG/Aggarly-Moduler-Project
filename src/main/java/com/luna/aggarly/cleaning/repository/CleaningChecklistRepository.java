package com.luna.aggarly.cleaning.repository;

import com.luna.aggarly.cleaning.entity.CleaningChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CleaningChecklistRepository extends JpaRepository<CleaningChecklist, UUID> {

    List<CleaningChecklist> findByCleaningTaskIdOrderByDisplayOrderAsc(UUID cleaningTaskId);
}
