-- Initialize auth database
-- Database is already created by POSTGRES_DB environment variable

-- Create auth schema
CREATE SCHEMA IF NOT EXISTS auth;

-- Set default schema
SET search_path TO auth, public;