package com.luna.aggarly.vision.repository;

import com.luna.aggarly.vision.entity.VisionEvaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VisionEvaluationRunRepository extends JpaRepository<VisionEvaluationRun, UUID> {

    List<VisionEvaluationRun> findAllByOrderByEvaluatedAtDesc();
}
