-- =============================================================================
-- V23: Cleaning Module Schema
-- =============================================================================

-- Cleaning Tasks Table
CREATE TABLE cleaning_tasks (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES properties(id),
    booking_id UUID REFERENCES bookings(id),
    host_id UUID NOT NULL REFERENCES users(id),
    assigned_cleaner_id UUID REFERENCES users(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    task_type VARCHAR(30) NOT NULL DEFAULT 'TURNOVER',
    scheduled_date DATE NOT NULL,
    scheduled_start_time TIME,
    estimated_duration_minutes INT NOT NULL DEFAULT 120,
    actual_started_at TIMESTAMP,
    actual_completed_at TIMESTAMP,
    cleaner_notes TEXT,
    host_feedback TEXT,
    rating_by_host INT CHECK (rating_by_host >= 1 AND rating_by_host <= 5),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_cleaning_tasks_property ON cleaning_tasks(property_id);
CREATE INDEX idx_cleaning_tasks_cleaner ON cleaning_tasks(assigned_cleaner_id);
CREATE INDEX idx_cleaning_tasks_host ON cleaning_tasks(host_id);
CREATE INDEX idx_cleaning_tasks_booking ON cleaning_tasks(booking_id);
CREATE INDEX idx_cleaning_tasks_scheduled ON cleaning_tasks(scheduled_date, status);

-- Cleaning Checklists Table
CREATE TABLE cleaning_checklists (
    id UUID PRIMARY KEY,
    cleaning_task_id UUID NOT NULL REFERENCES cleaning_tasks(id) ON DELETE CASCADE,
    room_name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_cleaning_checklists_task ON cleaning_checklists(cleaning_task_id);

-- Cleaning Checklist Items Table
CREATE TABLE cleaning_checklist_items (
    id UUID PRIMARY KEY,
    checklist_id UUID NOT NULL REFERENCES cleaning_checklists(id) ON DELETE CASCADE,
    task_description VARCHAR(255) NOT NULL,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP,
    notes VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_cleaning_items_checklist ON cleaning_checklist_items(checklist_id);

-- Cleaning Photo Evidence Table
CREATE TABLE cleaning_photos (
    id UUID PRIMARY KEY,
    cleaning_task_id UUID NOT NULL REFERENCES cleaning_tasks(id) ON DELETE CASCADE,
    room_name VARCHAR(100),
    photo_type VARCHAR(20) NOT NULL, -- BEFORE, AFTER, DAMAGE
    object_key VARCHAR(500) NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_cleaning_photos_task ON cleaning_photos(cleaning_task_id);

-- Cleaning Incident & Damage Issues Table
CREATE TABLE cleaning_issues (
    id UUID PRIMARY KEY,
    cleaning_task_id UUID NOT NULL REFERENCES cleaning_tasks(id),
    property_id UUID NOT NULL REFERENCES properties(id),
    booking_id UUID REFERENCES bookings(id),
    reported_by UUID NOT NULL REFERENCES users(id),
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM', -- LOW, MEDIUM, HIGH, BLOCKING
    photo_keys TEXT, -- comma-separated or JSON list of MinIO object keys
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolution_notes TEXT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_cleaning_issues_task ON cleaning_issues(cleaning_task_id);
CREATE INDEX idx_cleaning_issues_property ON cleaning_issues(property_id);
