-- ============================================================================
-- V32__create_vision_schema.sql
-- Aggarly Vision & Multimodal Perception Schema
-- ============================================================================

-- 1. Vision Model Registry
CREATE TABLE IF NOT EXISTS vision_model_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name VARCHAR(100) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    -- CLASSIFICATION | OBJECT_DETECTION | EMBEDDING | CAPTIONING | RERANKING | QUALITY
    embedding_dim INT,
    provider VARCHAR(50) NOT NULL,
    -- OLLAMA | NOMIC | LOCAL_ONNX | MOCK
    modality VARCHAR(30),
    -- IMAGE | TEXT | IMAGE_TEXT
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Provenance tracking
    prompt_version VARCHAR(20),
    preprocessing_version VARCHAR(20),
    model_revision VARCHAR(100),
    model_checksum VARCHAR(64),
    configuration_json JSONB,

    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (model_name, model_version)
);

-- Drop old V12 tables if present to recreate clean, rich metadata schema
DROP TABLE IF EXISTS property_image_features CASCADE;
DROP TABLE IF EXISTS property_image_tour_info CASCADE;
DROP TABLE IF EXISTS property_image_ai_metadata CASCADE;

-- 2. Property Image AI Metadata
CREATE TABLE property_image_ai_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_image_id UUID NOT NULL UNIQUE REFERENCES property_images(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,

    -- Pipeline Provenance
    pipeline_version VARCHAR(20) NOT NULL DEFAULT '1.0.0',
    processing_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    -- PENDING | PREPROCESSING | QUALITY_CHECK | INFERRING | EMBEDDING | COMPLETED | FAILED | SKIPPED
    current_stage VARCHAR(50),
    failure_reason TEXT,
    attempt_count INT NOT NULL DEFAULT 0,

    -- Model Traceability
    classification_model_id UUID REFERENCES vision_model_registry(id),
    embedding_model_id UUID REFERENCES vision_model_registry(id),
    prompt_version VARCHAR(20),
    preprocessing_version VARCHAR(20),
    last_processed_at TIMESTAMPTZ,

    -- Vector Index Consistency State
    vector_index_state VARCHAR(20) NOT NULL DEFAULT 'NOT_INDEXED',
    -- NOT_INDEXED | INDEXING | INDEXED | STALE | FAILED

    -- Perceptual Hash & Deduplication
    perceptual_hash VARCHAR(64),
    duplicate_classification VARCHAR(20) NOT NULL DEFAULT 'UNIQUE',
    -- UNIQUE | NEAR_DUPLICATE | EXACT_DUPLICATE
    duplicate_of_image_id UUID REFERENCES property_images(id) ON DELETE SET NULL,

    -- Quality Assessment (3 distinct dimensions)
    quality_score DOUBLE PRECISION,
    technical_quality_score DOUBLE PRECISION,
    visual_usability_score DOUBLE PRECISION,
    searchability_score DOUBLE PRECISION,
    sharpness_score DOUBLE PRECISION,
    brightness_score DOUBLE PRECISION,
    quality_grade VARCHAR(20),
    -- EXCELLENT | GOOD | ACCEPTABLE | POOR | REJECTED
    is_blurry BOOLEAN DEFAULT FALSE,
    is_dark BOOLEAN DEFAULT FALSE,
    is_overexposed BOOLEAN DEFAULT FALSE,
    is_screenshot BOOLEAN DEFAULT FALSE,
    is_collage BOOLEAN DEFAULT FALSE,
    is_relevant_to_property BOOLEAN DEFAULT TRUE,

    -- Scene & Classification
    scene_type VARCHAR(50),
    -- BEDROOM | BATHROOM | KITCHEN | LIVING_ROOM | DINING | BALCONY | POOL | EXTERIOR | VIEW | WORKSPACE | OTHER
    scene_confidence DOUBLE PRECISION,
    is_indoor BOOLEAN,
    view_type VARCHAR(50),
    -- SEA_VIEW | CITY_SKYLINE | MOUNTAIN | GARDEN | POOL_VIEW | STREET | COURTYARD | NONE
    room_cluster_id VARCHAR(64),

    -- Generated Descriptions & Text
    ai_caption VARCHAR(1000),
    alt_text VARCHAR(500),
    host_caption VARCHAR(500),
    ocr_text TEXT,
    visual_summary TEXT,

    -- Structured JSONB Extraction
    detected_objects_json JSONB,
    detected_amenities_json JSONB,
    style_tags_json JSONB,
    dominant_colors_json JSONB,
    condition_assessment_json JSONB,
    visual_features_json JSONB,
    visual_fit_json JSONB,

    -- Qdrant Vector Reference
    qdrant_point_id UUID,
    qdrant_collection VARCHAR(100),
    embedded_at TIMESTAMPTZ,

    -- Moderation
    moderation_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    moderation_reason VARCHAR(300),

    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_img_ai_property_id ON property_image_ai_metadata(property_id);
CREATE INDEX idx_img_ai_status ON property_image_ai_metadata(processing_status);
CREATE INDEX idx_img_ai_scene_type ON property_image_ai_metadata(scene_type);
CREATE INDEX idx_img_ai_phash ON property_image_ai_metadata(perceptual_hash);
CREATE INDEX idx_img_ai_qdrant_point ON property_image_ai_metadata(qdrant_point_id);
CREATE INDEX idx_img_ai_quality_grade ON property_image_ai_metadata(quality_grade);
CREATE INDEX idx_img_ai_index_state ON property_image_ai_metadata(vector_index_state);

-- 3. Property Visual Profiles
CREATE TABLE property_visual_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL UNIQUE REFERENCES properties(id) ON DELETE CASCADE,

    -- Profile State
    profile_status VARCHAR(30) NOT NULL DEFAULT 'INCOMPLETE',
    -- INCOMPLETE | READY | OUTDATED
    pipeline_version VARCHAR(20) NOT NULL DEFAULT '1.0.0',
    total_images INT NOT NULL DEFAULT 0,
    processed_images INT NOT NULL DEFAULT 0,
    usable_images INT NOT NULL DEFAULT 0,

    -- Coverage Analysis
    coverage_score DOUBLE PRECISION DEFAULT 0.0,
    missing_key_rooms_json JSONB,
    room_coverage_json JSONB,
    best_per_scene_json JSONB,

    -- Representative Images
    recommended_cover_image_id UUID REFERENCES property_images(id) ON DELETE SET NULL,
    representative_image_ids_json JSONB,

    -- Visual Fit Scores [0.0 - 1.0]
    romantic_score DOUBLE PRECISION DEFAULT 0.0,
    family_score DOUBLE PRECISION DEFAULT 0.0,
    luxury_score DOUBLE PRECISION DEFAULT 0.0,
    business_score DOUBLE PRECISION DEFAULT 0.0,
    relaxation_score DOUBLE PRECISION DEFAULT 0.0,

    -- Aggregated Metadata
    aggregated_style_tags_json JSONB,
    aggregated_amenities_json JSONB,
    dominant_palette_json JSONB,
    visual_fit_json JSONB,
    visual_summary TEXT,
    image_coverage_report_json JSONB,

    -- Qdrant Centroid Vector Reference
    qdrant_point_id UUID,
    qdrant_collection VARCHAR(100),
    embedding_model_id UUID REFERENCES vision_model_registry(id),
    embedded_at TIMESTAMPTZ,

    last_aggregated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_vpf_property_id ON property_visual_profiles(property_id);
CREATE INDEX idx_vpf_status ON property_visual_profiles(profile_status);

-- 4. Vision Processing Tasks
CREATE TABLE vision_processing_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    property_image_id UUID REFERENCES property_images(id) ON DELETE SET NULL,
    task_type VARCHAR(50) NOT NULL,
    -- PROCESS_IMAGE | GENERATE_PROPERTY_PROFILE | MIGRATE_EMBEDDINGS | REPROCESS_IMAGE
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    -- QUEUED | PROCESSING | COMPLETED | FAILED | DEAD_LETTER
    current_stage VARCHAR(50),
    priority INT NOT NULL DEFAULT 5,
    -- 1 = highest, 10 = lowest
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    last_error_message TEXT,
    retry_policy VARCHAR(20),
    worker_id VARCHAR(100),
    worker_instance_id VARCHAR(200),
    lease_until TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    scheduled_at TIMESTAMPTZ DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_vpt_status_priority_sched ON vision_processing_tasks(status, priority, scheduled_at);
CREATE INDEX idx_vpt_property ON vision_processing_tasks(property_id);
CREATE INDEX idx_vpt_image ON vision_processing_tasks(property_image_id);
CREATE INDEX idx_vpt_lease ON vision_processing_tasks(lease_until) WHERE status = 'PROCESSING';
