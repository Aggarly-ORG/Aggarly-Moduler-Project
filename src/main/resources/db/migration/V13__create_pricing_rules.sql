CREATE TABLE pricing_rules (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES properties(id),
    type VARCHAR(30) NOT NULL,
    start_date DATE,
    end_date DATE,
    adjustment_type VARCHAR(20) NOT NULL,
    adjustment_value NUMERIC(10,2) NOT NULL,
    threshold_value INT,
    priority INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,

    CONSTRAINT chk_seasonal_dates CHECK (
        type <> 'SEASONAL' OR (start_date IS NOT NULL AND end_date IS NOT NULL AND start_date < end_date)
    )
);

CREATE INDEX idx_pricing_rules_property ON pricing_rules(property_id);
CREATE INDEX idx_pricing_rules_property_type ON pricing_rules(property_id, type);

CREATE TABLE coupons (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    adjustment_type VARCHAR(20) NOT NULL,
    adjustment_value NUMERIC(10,2) NOT NULL,
    expires_at TIMESTAMP,
    max_redemptions INT,
    current_redemptions INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    min_subtotal NUMERIC(10,2),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP
);

CREATE TABLE coupon_redemptions (
    id UUID PRIMARY KEY,
    coupon_id UUID NOT NULL REFERENCES coupons(id),
    user_id UUID NOT NULL,
    booking_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,

    CONSTRAINT uq_coupon_user UNIQUE (coupon_id, user_id)
);

CREATE INDEX idx_coupons_code ON coupons(code);

CREATE TABLE tax_rules (
    id UUID PRIMARY KEY,
    region VARCHAR(50) NOT NULL,
    rate_percentage NUMERIC(5,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX idx_tax_rules_region ON tax_rules(region) WHERE is_deleted = false AND active = true;
