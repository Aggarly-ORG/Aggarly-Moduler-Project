-- ============================================================================
-- V33__create_vision_evaluation_schema.sql
-- Aggarly Vision Search Evaluation & Retrieval Quality Schema
-- ============================================================================

CREATE TABLE IF NOT EXISTS vision_eval_queries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_text TEXT,
    reference_image_key VARCHAR(500),
    query_type VARCHAR(30) NOT NULL,
    -- TEXT_ONLY | IMAGE_ONLY | MULTIMODAL
    city VARCHAR(100),
    min_guests INT,
    max_price_per_night DOUBLE PRECISION,
    expected_property_ids_json JSONB,
    relevance_grades_json JSONB,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS vision_evaluation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_version VARCHAR(20) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    ndcg_at5 DOUBLE PRECISION,
    ndcg_at10 DOUBLE PRECISION,
    recall_at5 DOUBLE PRECISION,
    recall_at10 DOUBLE PRECISION,
    precision_at5 DOUBLE PRECISION,
    mrr DOUBLE PRECISION,
    filter_correctness DOUBLE PRECISION,
    full_report_json JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_veq_query_type ON vision_eval_queries(query_type);
CREATE INDEX idx_ver_evaluated_at ON vision_evaluation_runs(evaluated_at);
