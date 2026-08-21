CREATE TABLE stored_files (
    id UUID PRIMARY KEY,
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    bucket VARCHAR(100) NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    extension VARCHAR(20),
    size BIGINT NOT NULL,
    checksum VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    owner_id UUID,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP WITH TIME ZONE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_stored_files_status_created_at ON stored_files(status, created_at);
CREATE INDEX idx_stored_files_owner_id ON stored_files(owner_id);
