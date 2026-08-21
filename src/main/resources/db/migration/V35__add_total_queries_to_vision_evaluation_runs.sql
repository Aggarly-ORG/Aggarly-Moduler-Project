-- ============================================================================
-- V35__add_total_queries_to_vision_evaluation_runs.sql
-- Add total query counter required by VisionEvaluationRun entity
-- ============================================================================

ALTER TABLE vision_evaluation_runs
    ADD COLUMN total_queries INT NOT NULL DEFAULT 0;