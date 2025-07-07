# VORTEX Authorization Service

Microsserviço de autenticação construído com Quarkus, integrado ao sistema VORTEX de controle de estoque.

## Funcionalidades Implementadas

### ✅ Autenticação Múltipla
- **Local**: Email e senha com hash bcrypt
- **OAuth2/OIDC**: Google e GitHub (configuração via environment variables)
- **JWT**: Tokens seguros com claims customizadas

### ✅ Gestão de Usuários
- Cadastro de novos usuários
- Recuperação de senha via email
- Sistema de bloqueio por tentativas excessivas
- Soft delete para usuários

### ✅ Segurança
- Validação robusta de senhas
- Tokens JWT com expiração de 8 horas
- Endpoints JWKS para validação distribuída
- CORS configurado para frontend React

### ✅ Auditoria
- Eventos publicados no Kafka (tópico: `vortex.auditoria`)
- Compatível com sistema de auditoria existente
- Logs detalhados de todas as operações

### ✅ Monitoramento
- Health checks em `/auth/health`
- Métricas Prometheus
- Logs estruturados

## Como Executar

### Pré-requisitos
- Java 17+
- Docker (para PostgreSQL e Kafka)
- Maven 3.8+

### 1. Configurar Database
```bash
# PostgreSQL via Docker
docker run --name vortex-auth-db -e POSTGRES_PASSWORD=vortex_pass -e POSTGRES_USER=vortex_user -e POSTGRES_DB=vortex_auth -p 5432:5432 -d postgres:13
```

### 2. Configurar Kafka (opcional)
```bash
# Usar o docker-compose do projeto principal
cd ../../../infra/docker
docker-compose -f docker-compose.kafka-simple.yml up -d
```

### 3. Executar Backend
```bash
# Modo desenvolvimento
./mvnw quarkus:dev

# Modo produção
./mvnw clean package -Pnative
./target/vortex-authorization-service-1.0.0-SNAPSHOT-runner
```

### 4. Configurar Variáveis de Ambiente

#### OAuth (opcional)
```bash
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
export GITHUB_CLIENT_ID=your-github-client-id
export GITHUB_CLIENT_SECRET=your-github-client-secret
```

#### Email (opcional)
```bash
export MAIL_USERNAME=your-smtp-username
export MAIL_PASSWORD=your-smtp-password
export MAIL_HOST=smtp.gmail.com
export MAIL_PORT=587
```

## Endpoints Principais

### Autenticação Local
- `POST /auth/login` - Login com email/senha
- `POST /auth/register` - Cadastro de usuário
- `POST /auth/forgot-password` - Solicitar recuperação
- `POST /auth/reset-password` - Redefinir senha

### OAuth
- `GET /auth/login/{provider}` - Iniciar OAuth (google/github)
- `GET /auth/callback/{provider}` - Callback OAuth

### JWKS
- `GET /auth/jwks.json` - Chaves públicas JWT
- `GET /auth/.well-known/openid-configuration` - Configuração OpenID

### Monitoramento
- `GET /auth/health` - Health check
- `GET /q/metrics` - Métricas Prometheus

## Integração com Sistema Principal

### 1. Validação de JWT
O sistema principal pode validar tokens usando o endpoint JWKS:
```
GET http://localhost:8081/auth/jwks.json
```

### 2. Eventos de Auditoria
Eventos são publicados no tópico Kafka `vortex.auditoria` com formato compatível:
```json
{
  "acao": "LOCAL_LOGIN_SUCCESS",
  "entidade": "USER",
  "entidadeId": 123,
  "userId": "user@example.com",
  "resultado": "SUCCESS",
  "detalhes": "Login via local",
  "timestamp": "2024-01-01T10:00:00"
}
```

### 3. CORS
Configurado para aceitar requests dos frontends:
- http://localhost:5173 (Vite dev)
- http://localhost:3000 (React dev)
- http://localhost:4173 (Vite preview)

## Arquitetura

```
src/main/java/br/com/vortex/login/
├── dto/              # Data Transfer Objects
├── exception/        # Exceções customizadas
├── model/           # Entidades JPA (User)
├── resource/        # REST Controllers
└── service/         # Lógica de negócio
    ├── AuthService     # Orquestração
    ├── LocalAuthService # Autenticação local
    ├── JwtService      # Geração JWT
    ├── EmailService    # Envio de emails
    ├── PasswordService # Hash bcrypt
    └── AuditService    # Eventos Kafka
```

## Banco de Dados

### Tabela `users`
- Suporte a múltiplos providers (local, google, github)
- Campos para recuperação de senha
- Sistema de bloqueio por tentativas
- Soft delete

### Tabela `user_roles`
- Roles: USER, ADMIN, MANAGER
- Relacionamento many-to-many com users

## Testes

```bash
# Testes unitários
./mvnw test

# Testes de integração
./mvnw verify
```

## Configuração de Produção

Para produção, ajuste as seguintes propriedades em `application.properties`:

```properties
# Database
quarkus.datasource.jdbc.url=jdbc:postgresql://prod-db:5432/vortex_auth
quarkus.datasource.username=${DB_USERNAME}
quarkus.datasource.password=${DB_PASSWORD}

# JWT
mp.jwt.verify.issuer=https://auth.yourdomain.com
smallrye.jwt.sign.key.location=classpath:privatekey.pem

# Email
quarkus.mailer.host=${MAIL_HOST}
quarkus.mailer.username=${MAIL_USERNAME}
quarkus.mailer.password=${MAIL_PASSWORD}

# Kafka
kafka.bootstrap.servers=${KAFKA_SERVERS}
```

## Segurança

- Senhas hasheadas com BCrypt
- Tokens JWT assinados com RS256
- Rate limiting por tentativas de login
- Logs sem exposição de dados sensíveis
- CORS configurado apropriadamente

---

## Quarkus Framework

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

### Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8081/q/dev/.

### Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it's not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

### Creating a native executable

You can create a native executable using: 
```shell script
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/vortex-authorization-service-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.