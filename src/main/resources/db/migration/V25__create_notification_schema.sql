-- =============================================================================
-- V25: Notification Module Schema
-- =============================================================================

-- Notifications Table
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    category VARCHAR(30) NOT NULL, -- BOOKING, PAYMENT, SECURITY, MESSAGES, CLEANING, REVIEWS, ALERTS
    channel VARCHAR(20) NOT NULL,  -- IN_APP, EMAIL, PUSH, SMS
    type VARCHAR(50) NOT NULL,     -- BOOKING_CONFIRMED, PAYMENT_FAILED, etc.
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    data_json TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'SENT', -- PENDING, SENT, DELIVERED, READ, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_category ON notifications(category);
CREATE INDEX idx_notifications_status ON notifications(status);

-- User Notification Preferences Table
CREATE TABLE user_notification_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    category VARCHAR(30) NOT NULL,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_user_notif_pref UNIQUE (user_id, category)
);

CREATE INDEX idx_user_notif_pref_user ON user_notification_preferences(user_id);

-- User & AI Watchdog Alerts Table (Price drops & Availability watches)
CREATE TABLE user_alerts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    alert_type VARCHAR(30) NOT NULL, -- PRICE_DROP, AVAILABILITY_OPEN, NEW_LISTING
    property_id UUID REFERENCES properties(id),
    city VARCHAR(100),
    target_price NUMERIC(10,2),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    triggered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_user_alerts_active ON user_alerts(is_active, alert_type);
CREATE INDEX idx_user_alerts_property ON user_alerts(property_id);
CREATE INDEX idx_user_alerts_user ON user_alerts(user_id);
