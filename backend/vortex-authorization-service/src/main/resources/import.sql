-- Insert default roles
INSERT INTO auth.roles (id, name, description) VALUES 
('550e8400-e29b-41d4-a716-446655440001', 'ADMIN', 'Administrador do sistema com acesso total'),
('550e8400-e29b-41d4-a716-446655440002', 'USER', 'Usuário padrão do sistema'),
('550e8400-e29b-41d4-a716-446655440003', 'MANAGER', 'Gerente com acesso intermediário'),
('550e8400-e29b-41d4-a716-446655440004', 'VIEWER', 'Usuário com acesso somente leitura');

-- Insert test users
-- Password for all: Test@123 (bcrypt hash: $2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW)
INSERT INTO auth.users (id, email, username, is_active, is_verified) VALUES 
('650e8400-e29b-41d4-a716-446655440001', 'admin@vortex.com', 'admin', true, true),
('650e8400-e29b-41d4-a716-446655440002', 'user@vortex.com', 'user', true, true),
('650e8400-e29b-41d4-a716-446655440003', 'manager@vortex.com', 'manager', true, true),
('650e8400-e29b-41d4-a716-446655440004', 'viewer@vortex.com', 'viewer', true, true),
('650e8400-e29b-41d4-a716-446655440005', 'inactive@vortex.com', 'inactive', false, true),
('650e8400-e29b-41d4-a716-446655440006', 'unverified@vortex.com', 'unverified', true, false);

-- Insert credentials (password: Test@123)
INSERT INTO auth.credentials (user_id, password_hash) VALUES 
('650e8400-e29b-41d4-a716-446655440001', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW'),
('650e8400-e29b-41d4-a716-446655440002', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW'),
('650e8400-e29b-41d4-a716-446655440003', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW'),
('650e8400-e29b-41d4-a716-446655440004', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW'),
('650e8400-e29b-41d4-a716-446655440005', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW'),
('650e8400-e29b-41d4-a716-446655440006', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW');

-- Assign roles to users
INSERT INTO auth.user_roles (user_id, role_id) VALUES 
('650e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001'), -- admin -> ADMIN
('650e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440002'), -- admin -> USER
('650e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440002'), -- user -> USER
('650e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440003'), -- manager -> MANAGER
('650e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440002'), -- manager -> USER
('650e8400-e29b-41d4-a716-446655440004', '550e8400-e29b-41d4-a716-446655440004'), -- viewer -> VIEWER
('650e8400-e29b-41d4-a716-446655440005', '550e8400-e29b-41d4-a716-446655440002'), -- inactive -> USER
('650e8400-e29b-41d4-a716-446655440006', '550e8400-e29b-41d4-a716-446655440002'); -- unverified -> USER

-- Insert sample audit logs
INSERT INTO auth.audit_logs (user_id, action, details, ip_address, user_agent) VALUES 
('650e8400-e29b-41d4-a716-446655440001', 'LOGIN_SUCCESS', '{"method": "password"}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/91.0'),
('650e8400-e29b-41d4-a716-446655440002', 'LOGIN_SUCCESS', '{"method": "password"}', '192.168.1.101', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/14.1'),
('650e8400-e29b-41d4-a716-446655440002', 'PASSWORD_CHANGED', '{"reason": "user_request"}', '192.168.1.101', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/14.1');

-- Insert sample login attempts for rate limiting testing
INSERT INTO auth.login_attempts (email, ip_address, success, attempted_at) VALUES 
('test@example.com', '192.168.1.50', false, CURRENT_TIMESTAMP - INTERVAL '20 minutes'),
('test@example.com', '192.168.1.50', false, CURRENT_TIMESTAMP - INTERVAL '19 minutes'),
('test@example.com', '192.168.1.50', false, CURRENT_TIMESTAMP - INTERVAL '18 minutes');