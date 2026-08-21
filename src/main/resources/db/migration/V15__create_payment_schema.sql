CREATE TABLE payments (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    gateway_payment_intent_id VARCHAR(255),
    gateway_customer_id VARCHAR(255),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    authorized_at TIMESTAMP,
    captured_at TIMESTAMP,
    total_refunded_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,

    CONSTRAINT chk_refund_not_exceeding CHECK (total_refunded_amount <= amount)
);

CREATE INDEX idx_payments_booking ON payments(booking_id);
CREATE INDEX idx_payments_user ON payments(user_id);
CREATE INDEX idx_payments_gateway_intent ON payments(gateway_payment_intent_id);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    result VARCHAR(20) NOT NULL,
    gateway_error_code VARCHAR(100),
    gateway_error_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_payment_attempts_payment ON payment_attempts(payment_id);

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    amount NUMERIC(10,2) NOT NULL,
    reason VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    gateway_refund_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,

    CONSTRAINT chk_refund_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_refunds_payment ON refunds(payment_id);

CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    gateway_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_webhook_events_gateway_event ON webhook_events(gateway_event_id);
CREATE INDEX idx_webhook_events_processed ON webhook_events(processed);