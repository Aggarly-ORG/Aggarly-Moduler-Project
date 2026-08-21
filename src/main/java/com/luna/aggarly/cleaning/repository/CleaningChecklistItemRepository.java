package com.luna.aggarly.cleaning.repository;

import com.luna.aggarly.cleaning.entity.CleaningChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CleaningChecklistItemRepository extends JpaRepository<CleaningChecklistItem, UUID> {

    List<CleaningChecklistItem> findByChecklistId(UUID checklistId);
}
