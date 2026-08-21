-- ===================================================================
-- V1__create_extensions.sql
-- Enable required PostgreSQL extensions
-- ===================================================================

-- UUID generation (uuid_generate_v4())
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- GiST index support for exclusion constraints (daterange overlap prevention)
CREATE EXTENSION IF NOT EXISTS "btree_gist";
