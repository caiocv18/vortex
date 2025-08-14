-- Fix missing migration history issue
-- This migration was referenced in the flyway_schema_history but the file was missing
-- Creating an empty migration to resolve the validation error

-- No changes needed - this is just to satisfy Flyway validation
SELECT 1;