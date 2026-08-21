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