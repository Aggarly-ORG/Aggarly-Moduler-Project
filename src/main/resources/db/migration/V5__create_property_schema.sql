CREATE TABLE properties (
    id UUID PRIMARY KEY,
    host_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    property_type VARCHAR(50) NOT NULL,
    max_guests INT NOT NULL,
    bedrooms INT NOT NULL,
    bathrooms INT NOT NULL,
    base_price_per_night DECIMAL(10, 2) NOT NULL,
    cancellation_policy VARCHAR(50) NOT NULL,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    avg_rating DECIMAL(3, 2) DEFAULT 0.0,
    review_count INT DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX idx_properties_host_id ON properties(host_id);
CREATE INDEX idx_properties_status ON properties(status);

CREATE TABLE addresses (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL UNIQUE REFERENCES properties(id) ON DELETE CASCADE,
    street VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    country VARCHAR(100) NOT NULL,
    zip_code VARCHAR(20)
);

CREATE INDEX idx_addresses_city_country ON addresses(city, country);

CREATE TABLE property_images (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    url VARCHAR(1024) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_cover BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE amenities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    icon VARCHAR(100),
    category VARCHAR(50) NOT NULL
);

CREATE TABLE property_amenities (
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    amenity_id BIGINT NOT NULL REFERENCES amenities(id) ON DELETE CASCADE,
    PRIMARY KEY (property_id, amenity_id)
);
