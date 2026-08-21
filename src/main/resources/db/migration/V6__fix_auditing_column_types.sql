ALTER TABLE properties
    ALTER COLUMN created_by TYPE UUID USING created_by::uuid,
    ALTER COLUMN updated_by TYPE UUID USING updated_by::uuid;
