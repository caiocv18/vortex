-- Fix ip_address column types to match Hibernate expectations
ALTER TABLE auth.audit_logs ALTER COLUMN ip_address TYPE varchar(255);
ALTER TABLE auth.login_attempts ALTER COLUMN ip_address TYPE varchar(255);