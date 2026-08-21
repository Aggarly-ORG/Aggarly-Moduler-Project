package com.luna.aggarly.vision.repository;

import com.luna.aggarly.vision.entity.VisionEvalQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VisionEvalQueryRepository extends JpaRepository<VisionEvalQuery, UUID> {

    List<VisionEvalQuery> findByQueryType(String queryType);
}
