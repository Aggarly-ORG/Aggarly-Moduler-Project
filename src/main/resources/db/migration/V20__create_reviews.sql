CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES properties(id),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    guest_id UUID NOT NULL,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT NOT NULL,
    cleanliness_rating INT CHECK (cleanliness_rating >= 1 AND cleanliness_rating <= 5),
    accuracy_rating INT CHECK (accuracy_rating >= 1 AND accuracy_rating <= 5),
    check_in_rating INT CHECK (check_in_rating >= 1 AND check_in_rating <= 5),
    communication_rating INT CHECK (communication_rating >= 1 AND communication_rating <= 5),
    location_rating INT CHECK (location_rating >= 1 AND location_rating <= 5),
    value_rating INT CHECK (value_rating >= 1 AND value_rating <= 5),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,

    CONSTRAINT uq_review_booking UNIQUE (booking_id)
);

CREATE INDEX idx_reviews_property ON reviews(property_id);
CREATE INDEX idx_reviews_guest ON reviews(guest_id);
