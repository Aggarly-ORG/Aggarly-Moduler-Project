package com.luna.aggarly.cleaning.repository;

import com.luna.aggarly.cleaning.entity.CleaningIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CleaningIssueRepository extends JpaRepository<CleaningIssue, UUID> {

    List<CleaningIssue> findByCleaningTaskId(UUID cleaningTaskId);

    Page<CleaningIssue> findByPropertyIdOrderByCreatedAtDesc(UUID propertyId, Pageable pageable);

    Page<CleaningIssue> findByResolvedFalseOrderByCreatedAtDesc(Pageable pageable);
}
