-- Insert default roles for testing
INSERT INTO auth.roles (id, name, description, created_at) VALUES 
('550e8400-e29b-41d4-a716-446655440001', 'USER', 'Default user role', CURRENT_TIMESTAMP),
('550e8400-e29b-41d4-a716-446655440002', 'ADMIN', 'Administrator role', CURRENT_TIMESTAMP);