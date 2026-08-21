-- Drop the constraint that depends on the 'deleted' column
ALTER TABLE availability_slots DROP CONSTRAINT IF EXISTS excl_no_overlap;

-- Rename the column to match BaseEntity's expectation
ALTER TABLE availability_slots RENAME COLUMN deleted TO is_deleted;

-- Recreate the constraint with the new column name
ALTER TABLE availability_slots 
    ADD CONSTRAINT excl_no_overlap EXCLUDE USING gist (
        property_id WITH =,
        date_range WITH &&
    ) WHERE (available = FALSE AND is_deleted = FALSE);
