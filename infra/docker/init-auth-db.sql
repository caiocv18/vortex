-- Initialize auth database
CREATE DATABASE vortex_auth;
\c vortex_auth;

-- Create auth schema
CREATE SCHEMA IF NOT EXISTS auth;

-- Set default schema
SET search_path TO auth, public;