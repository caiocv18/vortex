-- Increase token column sizes to accommodate longer JWT tokens
ALTER TABLE auth.refresh_tokens ALTER COLUMN token TYPE VARCHAR(1000);
ALTER TABLE auth.token_blacklist ALTER COLUMN token TYPE VARCHAR(1000);