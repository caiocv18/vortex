# Prompt para Implementação do Serviço de Autenticação Vortex

Preciso implementar um serviço completo de autenticação para o sistema Vortex. O serviço deve ser desenvolvido seguindo a arquitetura já estabelecida no projeto.

## Arquitetura Geral

### Backend - Quarkus (porta 8081)
- **Localização**: `backend/vortex-authorization-service/`
- **Banco de dados**: PostgreSQL 17 em container Docker
- **Schema**: Criar schema próprio `auth` com tabelas separadas
- **Autenticação**: JWT (JSON Web Tokens)
- **Comunicação**: REST API + Mensageria (Kafka/RabbitMQ conforme configurado)

### Frontend - React com Vite (porta 3001)
- **Localização**: `frontend/vortex-authorization-service/`
- **Roteamento**: React Router com páginas dedicadas
- **Estado**: Context API ou Redux Toolkit
- **Estilização**: CSS Modules com variáveis parametrizáveis

## Funcionalidades Requeridas

### 1. Páginas do Frontend
- **Login** (`/login`)
- **Cadastro** (`/register`)
- **Recuperação de Senha** (`/forgot-password`)
- **Redefinir Senha** (`/reset-password/:token`)

### 2. Estrutura do Banco de Dados

```sql
-- Schema auth
CREATE SCHEMA IF NOT EXISTS auth;

-- Tabela de usuários
CREATE TABLE auth.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT true,
    is_verified BOOLEAN DEFAULT false
);

-- Tabela de credenciais
CREATE TABLE auth.credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de roles
CREATE TABLE auth.roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de associação user-role
CREATE TABLE auth.user_roles (
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    role_id UUID REFERENCES auth.roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

-- Tabela de tokens de recuperação
CREATE TABLE auth.password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de refresh tokens
CREATE TABLE auth.refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token VARCHAR(500) UNIQUE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de blacklist de tokens
CREATE TABLE auth.token_blacklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(500) UNIQUE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de logs de auditoria
CREATE TABLE auth.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    details JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tabela de tentativas de login (rate limiting)
CREATE TABLE auth.login_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255),
    ip_address INET NOT NULL,
    success BOOLEAN DEFAULT false,
    attempted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### 3. Endpoints da API REST

```
POST   /api/auth/register          - Cadastro de novo usuário
POST   /api/auth/login            - Login (retorna access token e refresh token)
POST   /api/auth/logout           - Logout (invalida tokens)
POST   /api/auth/refresh          - Renovar access token
POST   /api/auth/forgot-password  - Solicitar recuperação de senha
POST   /api/auth/reset-password   - Redefinir senha com token
GET    /api/auth/verify-email/:token - Verificar email
GET    /api/auth/profile          - Obter perfil do usuário autenticado
PUT    /api/auth/profile          - Atualizar perfil
POST   /api/auth/change-password  - Alterar senha (usuário autenticado)
GET    /api/auth/sessions         - Listar sessões ativas
DELETE /api/auth/sessions/:id     - Revogar sessão específica
```

### 4. Eventos de Mensageria

Publicar os seguintes eventos no Kafka/RabbitMQ:
- `user.created` - Quando um novo usuário é criado
- `user.logged_in` - Quando usuário faz login
- `user.logged_out` - Quando usuário faz logout
- `password.changed` - Quando senha é alterada
- `password.reset_requested` - Quando recuperação é solicitada
- `email.verified` - Quando email é verificado

### 5. Configurações de Senha (Parametrizáveis)

Criar arquivo `application.properties` com:
```properties
# Password Policy
auth.password.min-length=8
auth.password.max-length=128
auth.password.require-uppercase=true
auth.password.require-lowercase=true
auth.password.require-numbers=true
auth.password.require-special-chars=true
auth.password.special-chars=!@#$%^&*()_+-=[]{}|;:,.<>?

# JWT Configuration
auth.jwt.secret=${JWT_SECRET:your-secret-key-here}
auth.jwt.access-token-expiration=15m
auth.jwt.refresh-token-expiration=7d
auth.jwt.issuer=vortex-auth-service

# Rate Limiting
auth.rate-limit.login-attempts=5
auth.rate-limit.window-minutes=15
auth.rate-limit.lockout-minutes=30

# Email Configuration
auth.email.from=noreply@vortex.com
auth.email.reset-token-expiration=1h
```

### 6. Design do Frontend

#### Estrutura de Arquivos CSS
```
frontend/vortex-authorization-service/src/styles/
├── variables.css      # Cores e variáveis parametrizáveis
├── global.css        # Estilos globais
├── animations.css    # Animações reutilizáveis
└── components/       # Estilos de componentes
```

#### variables.css
```css
:root {
  /* Cores Principais - Sincronizar com app principal */
  --primary-color: #1976d2;
  --primary-dark: #115293;
  --primary-light: #4791db;
  --secondary-color: #dc004e;
  --secondary-dark: #9a0036;
  --secondary-light: #f50057;
  
  /* Cores de Background */
  --bg-primary: #ffffff;
  --bg-secondary: #f5f5f5;
  --bg-dark: #121212;
  
  /* Cores de Texto */
  --text-primary: #333333;
  --text-secondary: #666666;
  --text-light: #999999;
  
  /* Estados */
  --success-color: #4caf50;
  --error-color: #f44336;
  --warning-color: #ff9800;
  --info-color: #2196f3;
  
  /* Sombras */
  --shadow-sm: 0 2px 4px rgba(0,0,0,0.1);
  --shadow-md: 0 4px 8px rgba(0,0,0,0.1);
  --shadow-lg: 0 8px 16px rgba(0,0,0,0.1);
  
  /* Bordas */
  --border-radius: 8px;
  --border-color: #e0e0e0;
  
  /* Transições */
  --transition-fast: 0.2s ease;
  --transition-normal: 0.3s ease;
}
```

#### Design das Páginas de Autenticação
- Layout com split screen: formulário à esquerda, imagem/branding à direita
- Formulários com campos flutuantes (floating labels)
- Botões com hover effects e loading states
- Validação em tempo real com feedback visual
- Responsivo para mobile
- Animações suaves entre páginas
- Ícones para melhor UX

### 7. Componente de Usuário Logado

Criar componente para o canto superior direito da aplicação principal:
```jsx
// UserMenu.jsx - Adicionar em frontend/vortex-application-service/src/components/
<div className="user-menu">
  <div className="user-info">
    <span className="user-name">{user.name}</span>
    <span className="user-email">{user.email}</span>
  </div>
  <button className="logout-btn" onClick={handleLogout}>
    <LogoutIcon /> Sair
  </button>
</div>
```

### 8. Segurança e Boas Práticas

1. **Rate Limiting**: Implementar para prevenir brute force
2. **CORS**: Configurar adequadamente entre portas 3001 e 8081
3. **HTTPS**: Preparar para produção com certificados SSL
4. **Sanitização**: Validar e sanitizar todas as entradas
5. **Logs de Auditoria**: Registrar todas as ações sensíveis
6. **Tokens Seguros**: Usar secrets fortes e rotacionar periodicamente
7. **Headers de Segurança**: X-Frame-Options, X-Content-Type-Options, etc.

### 9. Testes

#### Backend (Quarkus)
- Testes unitários para services e validators
- Testes de integração para endpoints REST
- Testes de contrato para eventos de mensageria
- Cobertura mínima: 80%

#### Frontend (React)
- Testes unitários com Jest e React Testing Library
- Testes E2E com Cypress para fluxos críticos:
  - Fluxo completo de registro
  - Login e logout
  - Recuperação de senha
- Testes de acessibilidade

### 10. Documentação

Criar em `docs/authentication/`:
- `README.md` - Visão geral do serviço
- `API.md` - Documentação da API REST
- `DATABASE.md` - Schema e modelos
- `SECURITY.md` - Práticas de segurança
- `CONTRIBUTING.md` - Guia para desenvolvedores
- `flow-diagrams/` - Diagramas de fluxo (Mermaid)
- Swagger/OpenAPI disponível em `/q/swagger-ui`

### 11. Integração com Sistema Principal

1. **Interceptor no Frontend Principal**: Adicionar axios interceptor para incluir token JWT
2. **Guard Routes**: Proteger rotas que requerem autenticação
3. **Context/Store**: Compartilhar estado de autenticação
4. **Redirecionamentos**: 
   - Não autenticado → `/login`
   - Após login → Dashboard principal
   - Após logout → Página inicial

### 12. Scripts e Configurações

Adicionar aos scripts existentes:
- Verificação de saúde do serviço de autorização
- Migração automática do banco de dados
- Seed de dados iniciais (roles padrão, usuário admin)

### 13. Dados de Teste (import.sql)

Criar arquivo `backend/vortex-authorization-service/src/main/resources/import.sql` com dados iniciais:

```sql
-- Inserir roles padrão
INSERT INTO auth.roles (id, name, description) VALUES 
('550e8400-e29b-41d4-a716-446655440001', 'ADMIN', 'Administrador do sistema com acesso total'),
('550e8400-e29b-41d4-a716-446655440002', 'USER', 'Usuário padrão do sistema'),
('550e8400-e29b-41d4-a716-446655440003', 'MANAGER', 'Gerente com acesso intermediário'),
('550e8400-e29b-41d4-a716-446655440004', 'VIEWER', 'Usuário com acesso somente leitura');

-- Inserir usuários de teste
-- Senha padrão para todos: Test@123
INSERT INTO auth.users (id, email, username, is_active, is_verified) VALUES 
('650e8400-e29b-41d4-a716-446655440001', 'admin@vortex.com', 'admin', true, true),
('650e8400-e29b-41d4-a716-446655440002', 'user@vortex.com', 'user', true, true),
('650e8400-e29b-41d4-a716-446655440003', 'manager@vortex.com', 'manager', true, true),
('650e8400-e29b-41d4-a716-446655440004', 'viewer@vortex.com', 'viewer', true, true),
('650e8400-e29b-41d4-a716-446655440005', 'inactive@vortex.com', 'inactive', false, true),
('650e8400-e29b-41d4-a716-446655440006', 'unverified@vortex.com', 'unverified', true, false);

-- Inserir credenciais (senha: Test@123 - bcrypt hash)
INSERT INTO auth.credentials (user_id, password_hash) VALUES 
('650e8400-e29b-41d4-a716-446655440001', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW'),
('650e8400-e29b-41d4-a716-446655440002', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW'),
('650e8400-e29b-41d4-a716-446655440003', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW'),
('650e8400-e29b-41d4-a716-446655440004', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW'),
('650e8400-e29b-41d4-a716-446655440005', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW'),
('650e8400-e29b-41d4-a716-446655440006', '$2a$10$5vvbROzmmXGkfPVaZTyNOuSgD3KHdxHhgNJL2PWbXkUFqGJqYdcmW');

-- Associar roles aos usuários
INSERT INTO auth.user_roles (user_id, role_id) VALUES 
('650e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001'), -- admin -> ADMIN
('650e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440002'), -- admin -> USER
('650e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440002'), -- user -> USER
('650e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440003'), -- manager -> MANAGER
('650e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440002'), -- manager -> USER
('650e8400-e29b-41d4-a716-446655440004', '550e8400-e29b-41d4-a716-446655440004'), -- viewer -> VIEWER
('650e8400-e29b-41d4-a716-446655440005', '550e8400-e29b-41d4-a716-446655440002'), -- inactive -> USER
('650e8400-e29b-41d4-a716-446655440006', '550e8400-e29b-41d4-a716-446655440002'); -- unverified -> USER

-- Inserir alguns logs de auditoria de exemplo
INSERT INTO auth.audit_logs (user_id, action, details, ip_address, user_agent) VALUES 
('650e8400-e29b-41d4-a716-446655440001', 'LOGIN_SUCCESS', '{"method": "password"}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/91.0'),
('650e8400-e29b-41d4-a716-446655440002', 'LOGIN_SUCCESS', '{"method": "password"}', '192.168.1.101', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/14.1'),
('650e8400-e29b-41d4-a716-446655440002', 'PASSWORD_CHANGED', '{"reason": "user_request"}', '192.168.1.101', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/14.1');

-- Inserir tentativas de login para teste de rate limiting
INSERT INTO auth.login_attempts (email, ip_address, success, attempted_at) VALUES 
('test@example.com', '192.168.1.50', false, CURRENT_TIMESTAMP - INTERVAL '20 minutes'),
('test@example.com', '192.168.1.50', false, CURRENT_TIMESTAMP - INTERVAL '19 minutes'),
('test@example.com', '192.168.1.50', false, CURRENT_TIMESTAMP - INTERVAL '18 minutes');
```

#### Informações dos Usuários de Teste:

| Email | Username | Senha | Role(s) | Status |
|-------|----------|-------|---------|---------|
| admin@vortex.com | admin | Test@123 | ADMIN, USER | Ativo e Verificado |
| user@vortex.com | user | Test@123 | USER | Ativo e Verificado |
| manager@vortex.com | manager | Test@123 | MANAGER, USER | Ativo e Verificado |
| viewer@vortex.com | viewer | Test@123 | VIEWER | Ativo e Verificado |
| inactive@vortex.com | inactive | Test@123 | USER | Inativo |
| unverified@vortex.com | unverified | Test@123 | USER | Não Verificado |

## Arquivos Existentes Relevantes

@docs/PORTS.md
@start-vortex.sh
@frontend/vortex-application-service/package.json
@frontend/vortex-application-service/src/App.vue
@frontend/vortex-application-service/src/router/index.js
@frontend/vortex-application-service/src/main.js
@backend/vortex-application-service/pom.xml
@backend/vortex-application-service/src/main/resources/application.properties
@backend/vortex-application-service/src/main/java/com/vortex/application/VortexApplication.java
@infra/docker/docker-compose.yml
@infra/docker/docker-compose.full.yml
@infra/docker/docker-compose.auth.yml

---

Este prompt fornece todas as especificações necessárias para implementar um serviço de autenticação completo, seguro e bem integrado com o sistema Vortex existente.