ALTER TABLE property_images
    ADD COLUMN width_px INT,
    ADD COLUMN height_px INT,
    ADD COLUMN file_size_bytes BIGINT,
    ADD COLUMN content_type VARCHAR(50);

CREATE TABLE property_image_tour_info (
    id UUID PRIMARY KEY,
    property_image_id UUID NOT NULL UNIQUE REFERENCES property_images(id),
    room_type VARCHAR(30),
    room_label VARCHAR(100)
);

CREATE TABLE property_image_features (
    id UUID PRIMARY KEY,
    tour_info_id UUID NOT NULL REFERENCES property_image_tour_info(id),
    feature_name VARCHAR(100) NOT NULL,
    feature_category VARCHAR(30) NOT NULL,
    source VARCHAR(20) NOT NULL,
    confidence_score DOUBLE PRECISION
);

CREATE TABLE property_image_ai_metadata (
    id UUID PRIMARY KEY,
    property_image_id UUID NOT NULL UNIQUE REFERENCES property_images(id),
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    host_caption VARCHAR(500),
    ai_caption VARCHAR(500),
    alt_text VARCHAR(300),
    ocr_text TEXT,
    detected_objects_json TEXT,
    style_tags_json TEXT,
    qdrant_point_id UUID,
    qdrant_collection VARCHAR(100),
    embedding_model VARCHAR(100),
    embedded_at TIMESTAMP,
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    moderation_reason VARCHAR(300)
);

CREATE INDEX idx_tour_info_room_type ON property_image_tour_info(room_type);
CREATE INDEX idx_features_tour_info ON property_image_features(tour_info_id);
CREATE INDEX idx_ai_metadata_processing_status ON property_image_ai_metadata(processing_status);
CREATE INDEX idx_ai_metadata_qdrant_point ON property_image_ai_metadata(qdrant_point_id);