CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE availability_slots (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES properties(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    date_range DATERANGE GENERATED ALWAYS AS (daterange(start_date, end_date, '[)')) STORED,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    block_reason VARCHAR(20),
    booking_id UUID,
    
    -- Base entity fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,

    CONSTRAINT chk_date_order CHECK (start_date < end_date),

    -- The core guarantee: no two NON-soft-deleted, UNAVAILABLE rows for the
    -- same property may have overlapping date ranges.
    CONSTRAINT excl_no_overlap EXCLUDE USING gist (
        property_id WITH =,
        date_range WITH &&
    ) WHERE (available = FALSE AND deleted = FALSE)
);

CREATE INDEX idx_availability_property ON availability_slots(property_id);
CREATE INDEX idx_availability_daterange ON availability_slots USING gist (date_range);
