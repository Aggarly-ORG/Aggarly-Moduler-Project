CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES properties(id),
    guest_id UUID NOT NULL,
    host_id UUID NOT NULL,
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    guest_count INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    price_breakdown_json TEXT NOT NULL,
    coupon_code VARCHAR(50),
    cancellation_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,

    CONSTRAINT chk_booking_dates CHECK (check_in < check_out)
);

CREATE INDEX idx_bookings_guest ON bookings(guest_id);
CREATE INDEX idx_bookings_host ON bookings(host_id);
CREATE INDEX idx_bookings_property ON bookings(property_id);
CREATE INDEX idx_bookings_status ON bookings(status);
