-- Os quatro papéis só existiam no import.sql, que o Hibernate carrega apenas nos testes. Fora deles
-- auth.roles ficava vazia: Role.findUserRole() voltava null e o cadastro gravava o usuário sem papel.
-- Os ids são os mesmos do import.sql. ON CONFLICT torna a migração segura num banco que já os tenha.
INSERT INTO auth.roles (id, name, description) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'ADMIN', 'Administrador do sistema com acesso total'),
    ('550e8400-e29b-41d4-a716-446655440002', 'USER', 'Usuário padrão do sistema'),
    ('550e8400-e29b-41d4-a716-446655440003', 'MANAGER', 'Gerente com acesso intermediário'),
    ('550e8400-e29b-41d4-a716-446655440004', 'VIEWER', 'Usuário com acesso somente leitura')
ON CONFLICT (name) DO NOTHING;

-- Quem se cadastrou com a tabela vazia ficou sem papel nenhum. Recebe o USER que o cadastro
-- (AuthService.register) teria dado se o papel existisse. O LEFT JOIN com IS NULL é o anti-join:
-- quem já tem algum papel some do resultado, então rodar de novo não duplica nem troca papel.
INSERT INTO auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM auth.users u
JOIN auth.roles r ON r.name = 'USER'
LEFT JOIN auth.user_roles ur ON ur.user_id = u.id
WHERE ur.user_id IS NULL;
