# Vortex Authorization Service

Serviço de autorização e autenticação do sistema Vortex, implementado com Quarkus e focado em alta performance e segurança.

## 🔐 Visão Geral

Este serviço oferece autenticação JWT completa, incluindo registro de usuários, login, refresh tokens e recuperação de senha, integrado com messaging para auditoria de eventos de segurança.

### Funcionalidades Principais
- ✅ **Registro de usuários** com validação robusta
- ✅ **Autenticação JWT** com access e refresh tokens
- ✅ **Recuperação de senha** por email
- ✅ **Rate limiting** para proteção contra ataques
- ✅ **Auditoria de segurança** via messaging
- ✅ **Validação de políticas** de senha complexa
- ✅ **Criptografia BCrypt** com 12 rounds

## 🛠 Tecnologias

- **Java 17** - Linguagem principal
- **Quarkus 3.8.5** - Framework reativo
- **PostgreSQL** - Banco de dados (H2 para testes)
- **BCrypt** - Criptografia de senhas
- **JWT** - Autenticação stateless
- **Jakarta Bean Validation** - Validação de dados
- **Panache** - ORM simplificado
- **SmallRye Messaging** - Integração com Kafka/RabbitMQ

## 🚀 Quick Start

### Pré-requisitos
- Java 17+
- Maven 3.8+
- PostgreSQL 13+ (opcional, usa H2 por padrão)

### Execução Local
```bash
# Modo desenvolvimento (H2 database)
mvn quarkus:dev

# Com PostgreSQL
mvn quarkus:dev -Dquarkus.profile=postgres

# Build para produção
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

### Usando o Launcher Principal
```bash
# Do diretório raiz do projeto
./start-vortex.sh --auth-only
```

## 📡 API Endpoints

### Base URL
- **Development**: `http://localhost:8081/api/auth`
- **Documentation**: `http://localhost:8081/q/swagger-ui`
- **Health Check**: `http://localhost:8081/q/health`

### Autenticação

#### POST /api/auth/register
Registra novo usuário no sistema.

**Request Body:**
```json
{
  "email": "user@example.com",
  "username": "johndoe",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123"
}
```

**Response (201):**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "rt_a1b2c3d4...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "username": "johndoe",
    "isActive": true,
    "isVerified": false,
    "roles": ["USER"]
  }
}
```

#### POST /api/auth/login
Autentica usuário existente.

**Request Body:**
```json
{
  "identifier": "user@example.com", // email ou username
  "password": "SecurePass@123"
}
```

**Response (200):**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "rt_a1b2c3d4...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "username": "johndoe",
    "isActive": true,
    "isVerified": true,
    "roles": ["USER"]
  }
}
```

#### POST /api/auth/refresh
Renova access token usando refresh token.

**Request Body:**
```json
{
  "refreshToken": "rt_a1b2c3d4..."
}
```

#### POST /api/auth/logout
Revoga refresh token (logout).

**Request Body:**
```json
{
  "refreshToken": "rt_a1b2c3d4..."
}
```

#### POST /api/auth/forgot-password
Inicia processo de recuperação de senha.

**Request Body:**
```json
{
  "email": "user@example.com"
}
```

#### POST /api/auth/reset-password
Reset senha usando token de recuperação.

**Request Body:**
```json
{
  "token": "reset_token_here",
  "password": "NewSecurePass@456",
  "confirmPassword": "NewSecurePass@456"
}
```

#### POST /api/auth/validate-token
Valida se um JWT token é válido.

**Request Body:**
```json
{
  "token": "eyJhbGciOiJSUzI1NiJ9..."
}
```

**Response:**
```json
{
  "valid": true,
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "johndoe",
  "roles": ["USER"]
}
```

## 🏗 Arquitetura

### Estrutura do Projeto
```
src/main/java/br/com/vortex/authorization/
├── config/           # Configurações (Messaging)
├── dto/              # Data Transfer Objects
├── entity/           # Entidades JPA (User, Role, Token, etc.)
├── event/            # Eventos de auditoria
├── resource/         # Controllers REST (JAX-RS)
├── security/         # JWT e Password services
└── service/          # Lógica de negócio
```

### Entidades Principais

#### User
- `id` (UUID) - Identificador único
- `email` (String, unique) - Email do usuário
- `username` (String, unique) - Nome de usuário
- `isActive` (Boolean) - Status ativo
- `isVerified` (Boolean) - Email verificado
- `createdAt` / `updatedAt` - Timestamps

#### Credential
- `user` (User) - Relacionamento com usuário
- `passwordHash` (String) - Hash BCrypt da senha
- `createdAt` / `updatedAt` - Timestamps

#### RefreshToken
- `user` (User) - Relacionamento com usuário
- `token` (String) - Token de refresh
- `expiresAt` (OffsetDateTime) - Data de expiração
- `revoked` (Boolean) - Status de revogação

#### Role
- `name` (String) - Nome da role (USER, ADMIN)
- `description` (String) - Descrição da role

### Políticas de Senha
```yaml
Comprimento: 8-128 caracteres
Obrigatório:
  - Pelo menos 1 letra maiúscula
  - Pelo menos 1 letra minúscula  
  - Pelo menos 1 número
  - Pelo menos 1 caracter especial (!@#$%^&*()_+-=[]{}|;:,.<>?)
```

### JWT Configuration
- **Algorithm**: RS256 (RSA with SHA-256)
- **Access Token TTL**: 1 hora
- **Refresh Token TTL**: 7 dias
- **Keys**: RSA 2048-bit (privateKey.pem / publicKey.pem), ver [Chaves RSA do JWT](#chaves-rsa-do-jwt)

### Chaves RSA do JWT

O serviço assina os tokens com `src/main/resources/jwt/privateKey.pem` e os confere com `src/main/resources/jwt/publicKey.pem`. No `application.properties` são `smallrye.jwt.sign.key.location=jwt/privateKey.pem` e `mp.jwt.verify.publickey.location=jwt/publicKey.pem`, caminhos no classpath. Os dois arquivos ficam fora do git (`*.pem` no `.gitignore`) e entram no jar pelo `mvn package`, em `jwt/`.

- O `start-vortex.sh` gera o par quando ele falta.
- Para gerar à mão, de dentro de `backend/vortex-authorization-service`:

```bash
mkdir -p src/main/resources/jwt
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out src/main/resources/jwt/privateKey.pem
openssl pkey -in src/main/resources/jwt/privateKey.pem -pubout -out src/main/resources/jwt/publicKey.pem
```

O `genpkey` grava a chave privada em PKCS#8 (`BEGIN PRIVATE KEY`), o formato que o SmallRye JWT lê. Trocar o par invalida todos os tokens já emitidos, inclusive os refresh tokens.

**Checkout antigo.** Até a atualização de novembro de 2026 as chaves ficavam em `src/main/resources/META-INF/resources/`, pasta que o Quarkus serve como arquivo estático: `GET /privateKey.pem` entregava a chave de assinatura. O git não move arquivo ignorado, então num checkout antigo mova os dois `.pem` para `src/main/resources/jwt/` e apague os de `META-INF/resources/`. Sem isso o serviço sobe e o `/q/health` fica verde, porque as chaves só são lidas no primeiro uso, mas todo login e todo cadastro falham com 500.

Para usar chaves fora do classpath (montadas num container, por exemplo), sobrescreva os dois caminhos pelas variáveis `SMALLRYE_JWT_SIGN_KEY_LOCATION` e `MP_JWT_VERIFY_PUBLICKEY_LOCATION`, com o prefixo `file:` (`file:/run/secrets/privateKey.pem`).

A suíte de testes não usa essas chaves: ela tem um par próprio, versionado, em `src/test/resources/jwt-test/`.

## 🧪 Testes

### Estratégia Implementada

#### Testes de Registro de Usuário (42 testes)
- **3 categorias**: Unitários, Integração e Validação
- **Environment isolado** sem dependências externas
- **BCrypt testing** com configuração de 12 rounds
- **JaCoCo Coverage** com relatórios HTML, XML e CSV automáticos
- **Quality Gates** com cobertura mínima configurada

#### Testes de Recuperação de Senha (56 testes) ✅ 100% ESTÁVEIS
- **4 categorias especializadas**: Service, Resource, Security, Validation
- **Cobertura completa** do fluxo de password recovery
- **Testes de segurança** focados em vulnerabilidades e ataques
- **Validação abrangente** de DTOs e políticas de senha
- **Correções implementadas**: Rate limiting, token invalidation, concurrent handling, timing attacks

### Execução dos Testes

#### Testes de Registro
```bash
# Script especializado (recomendado)
./scripts/run-auth-registration-tests.sh --all

# Testes unitários apenas
./scripts/run-auth-registration-tests.sh --unit

# Com cobertura de código JaCoCo
./scripts/run-auth-registration-tests.sh --coverage

# Modo CI completo (coverage + reports)
./scripts/run-auth-registration-tests.sh --ci

# Modo watch para desenvolvimento
./scripts/run-auth-registration-tests.sh --watch
```

#### Testes de Recuperação de Senha
```bash
# Todos os testes de password recovery (56 testes)
./scripts/run-auth-password-recovery-tests.sh --all

# Por categoria específica
./scripts/run-auth-password-recovery-tests.sh --unit        # Service tests (15)
./scripts/run-auth-password-recovery-tests.sh --integration # Resource tests (15)
./scripts/run-auth-password-recovery-tests.sh --security    # Security tests (8)
./scripts/run-auth-password-recovery-tests.sh --validation  # Validation tests (18)

# Com cobertura de código JaCoCo
./scripts/run-auth-password-recovery-tests.sh --coverage

# Modo CI completo (coverage + reports)
./scripts/run-auth-password-recovery-tests.sh --ci

# Modo watch para desenvolvimento
./scripts/run-auth-password-recovery-tests.sh --watch

# Execução rápida (skip tests lentos)
./scripts/run-auth-password-recovery-tests.sh --quick
```

#### Maven Tradicional
```bash
# Todos os testes
mvn test

# Apenas testes específicos
mvn test -Dtest="*PasswordRecovery*"
mvn test -Dtest="SimplePasswordServiceTest"

# Com cobertura manual
mvn test jacoco:report
```

### Cobertura de Testes

#### Registro de Usuário
- **SimplePasswordServiceTest**: 24 testes (validação e criptografia)
- **ValidationTest**: 10 testes (DTOs com Bean Validation)
- **SimpleAuthServiceTest**: 8 testes (estruturas e TestDataBuilder)

#### Recuperação de Senha
- **PasswordRecoveryServiceTest**: 15 testes (lógica de negócio)
- **PasswordRecoveryResourceTest**: 15 testes (endpoints REST)
- **PasswordRecoverySecurityTest**: 8 testes (segurança e vulnerabilidades)
- **PasswordRecoveryValidationTest**: 18 testes (validação de DTOs)

### Relatórios de Cobertura
- **HTML Visual**: `coverage-reports/auth-registration/index.html`
- **XML CI/CD**: `coverage-reports/auth-registration/jacoco.xml`
- **CSV Dados**: `coverage-reports/auth-registration/jacoco.csv`
- **Cobertura Atual**: ~64% PasswordService (área principal dos testes)

## 🔧 Configuração

### Variáveis de Ambiente
```bash
# Database
QUARKUS_DATASOURCE_USERNAME=vortex_auth
QUARKUS_DATASOURCE_PASSWORD=secure_password
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/vortex_auth

# JWT (padrão: jwt/privateKey.pem e jwt/publicKey.pem no classpath)
SMALLRYE_JWT_SIGN_KEY_LOCATION=file:/path/to/privateKey.pem
MP_JWT_VERIFY_PUBLICKEY_LOCATION=file:/path/to/publicKey.pem

# Proxy reverso: IPs ou CIDRs cujo X-Forwarded-For vale como IP do cliente.
# Sem a variável, nenhum proxy é confiável e o cabeçalho é ignorado.
TRUSTED_PROXIES=10.0.0.5

# Eventos de autenticação (precisa do perfil kafka ou rabbitmq)
AUTH_EVENT_ENABLED=false

# Password Policy
AUTH_PASSWORD_MIN_LENGTH=8
AUTH_PASSWORD_MAX_LENGTH=128
AUTH_PASSWORD_REQUIRE_UPPERCASE=true
AUTH_PASSWORD_REQUIRE_LOWERCASE=true
AUTH_PASSWORD_REQUIRE_NUMBERS=true
AUTH_PASSWORD_REQUIRE_SPECIAL_CHARS=true

# Rate Limiting do login
AUTH_RATE_LIMIT_LOGIN_ATTEMPTS=5
AUTH_RATE_LIMIT_WINDOW_MINUTES=15
```

### Profiles Disponíveis
- `dev` - H2 database, logging debug
- `test` - H2 in-memory, ideal para testes
- `postgres` - PostgreSQL, produção local
- `prod` - PostgreSQL, configurações de produção

### Messaging Configuration
Os eventos saem pelo canal `user-events` e ficam desligados por padrão. Para ligar: `AUTH_EVENT_ENABLED=true` e o perfil `kafka` (tópico `vortex-user-events`) ou `rabbitmq` (exchange `vortex.user.events`), por exemplo `-Dquarkus.profile=leve,kafka`.

```properties
# application.properties (perfil kafka)
%kafka.mp.messaging.outgoing.user-events.connector=smallrye-kafka
%kafka.mp.messaging.outgoing.user-events.topic=vortex-user-events
# Produtor idempotente: o kafka-clients exige acks=all
%kafka.kafka.acks=all
%kafka.kafka.enable.idempotence=true
```

- Um evento só sai depois do commit da transação que o gerou; no rollback ele é descartado.
- Nenhum evento leva o refresh token. O `sessionId` de login e logout é o id da linha do refresh token, que identifica a sessão mas não a renova.
- No modo dev o `AuthEventConsumer` de exemplo também lê o canal, junto com o conector (`@Broadcast` no emissor).

## 📊 Monitoring & Health

### Health Checks
```bash
curl http://localhost:8081/q/health
```

**Response:**
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Database connections health check",
      "status": "UP"
    },
    {
      "name": "Messaging health check",
      "status": "UP"
    }
  ]
}
```

### Metrics
- **Endpoint**: `/q/metrics`
- **Format**: Prometheus
- **Includes**: JVM, Database, HTTP, Custom business metrics

### Code Coverage (JaCoCo)
- **Endpoint**: `coverage-reports/auth-registration/index.html`
- **Formats**: HTML (visual), XML (CI/CD), CSV (data analysis)
- **Configuração**: Plugin Maven integrado com quality gates
- **Critérios**: Mínimo 60% cobertura de linhas, 50% branches

### Logging
```yaml
# Structured logging em JSON
quarkus.log.console.json=true
quarkus.log.level=INFO
quarkus.log.category."br.com.vortex.authorization".level=DEBUG
```

## 🔒 Segurança

### Rate Limiting
O limite vale para o login e barra também o `forgot-password`. Por padrão são 5 falhas em 15 minutos (`AUTH_RATE_LIMIT_LOGIN_ATTEMPTS`, `AUTH_RATE_LIMIT_WINDOW_MINUTES`).

- As falhas recentes contam por e-mail **ou** por IP: 5 falhas de um IP bloqueiam esse IP para qualquer conta, e 5 falhas numa conta a bloqueiam de qualquer IP.
- A tentativa barrada pelo limite não conta como falha nova. O bloqueio acaba sozinho quando as falhas que o causaram saem da janela, mesmo que alguém continue tentando.
- A falha é gravada no mesmo commit do login, na conexão que ele já usa: logins errados simultâneos não esgotam o pool.
- O IP é o da conexão. O `X-Forwarded-For` só vale vindo de um endereço de `TRUSTED_PROXIES`, que por padrão não tem nenhum; um valor que não é IP literal é descartado. Atrás de um proxy, ele deve sobrescrever o cabeçalho (no nginx, `proxy_set_header X-Forwarded-For $remote_addr`), porque o Quarkus usa o primeiro valor da lista.
- Login com falha interna (banco fora, pool esgotado, chave ausente) responde 500 com mensagem genérica, não 401.

### Auditoria
Todos os eventos são auditados via messaging:
- `UserCreatedEvent` - Novo usuário registrado
- `UserLoggedInEvent` - Login bem-sucedido
- `UserLoggedOutEvent` - Logout do usuário
- `PasswordChangedEvent` - Senha alterada
- `PasswordResetRequestedEvent` - Reset de senha solicitado

### Headers de Segurança
```yaml
quarkus.http.cors=true
quarkus.http.cors.origins=http://localhost:3001,http://localhost:5173
quarkus.http.cors.headers=accept,authorization,content-type,x-requested-with
quarkus.http.cors.methods=GET,POST,PUT,DELETE,OPTIONS
```

## 🚀 Deploy

### Docker Build
```bash
mvn package
docker build -f src/main/docker/Dockerfile.jvm -t vortex/auth-service .
docker run -p 8081:8081 vortex/auth-service
```

### Native Build
```bash
mvn package -Pnative
docker build -f src/main/docker/Dockerfile.native -t vortex/auth-service-native .
```

### Kubernetes
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vortex-auth-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: vortex-auth-service
  template:
    metadata:
      labels:
        app: vortex-auth-service
    spec:
      containers:
      - name: auth-service
        image: vortex/auth-service:latest
        ports:
        - containerPort: 8081
        env:
        - name: QUARKUS_DATASOURCE_JDBC_URL
          value: "jdbc:postgresql://postgres-service:5432/vortex_auth"
```

## 🛠 Desenvolvimento

### IDE Setup
- **IntelliJ IDEA**: Import como projeto Maven
- **VS Code**: Extension Pack for Java + Quarkus

### Debug Mode
```bash
mvn quarkus:dev -Ddebug=true
# Connect debugger to port 5005
```

### Live Reload
O Quarkus oferece hot reload automático em modo dev:
```bash
mvn quarkus:dev
# Modificações em código são aplicadas automaticamente
```

### Database Console (H2)
```bash
# Modo dev com H2
mvn quarkus:dev
# Acesse: http://localhost:8081/q/dev
# Database console disponível na interface de desenvolvimento
```

## 📋 TODO / Roadmap

### Próximas Implementações
- [ ] Email verification flow
- [ ] OAuth2 integration (Google, GitHub)
- [ ] Multi-factor authentication (MFA)
- [ ] Password history validation
- [ ] Account lockout mechanism
- [ ] CAPTCHA integration
- [ ] Session management dashboard

### Melhorias Técnicas
- [x] JaCoCo plugin para coverage reports (✅ Implementado)
- [x] Comprehensive password recovery test suite (✅ 56 testes estáveis)
- [x] Security vulnerability testing (✅ Rate limiting, timing attacks, token reuse)
- [x] Concurrent operation testing (✅ Thread-safe token handling)
- [ ] Performance benchmarks com JMH
- [ ] Load testing com Gatling
- [ ] Security scanning com OWASP ZAP
- [ ] Contract testing com Pact
- [ ] Integração com SonarQube para métricas avançadas

## 🐛 Troubleshooting

### Problemas Comuns

#### Port 8081 já em uso
```bash
# Verificar processos na porta
lsof -i :8081

# Matar processo específico
kill -9 <PID>

# Ou usar porta alternativa
mvn quarkus:dev -Dquarkus.http.port=8082
```

#### Database connection failed
```bash
# Verificar PostgreSQL status
sudo systemctl status postgresql

# Resetar para H2 (desenvolvimento)
mvn quarkus:dev -Dquarkus.profile=dev
```

#### JWT keys not found
As chaves ficam em `src/main/resources/jwt/` (não na raiz de `src/main/resources` nem em `META-INF/resources`). Onde ficam, como gerar e como migrar um checkout antigo: [Chaves RSA do JWT](#chaves-rsa-do-jwt).

#### Tests failing
```bash
# Executar testes com debug
mvn test -X

# Apenas testes específicos
mvn test -Dtest="SimplePasswordServiceTest"

# Com profile específico
mvn test -Dquarkus.test.profile=test
```

## 📚 Referências

### Documentação
- [Quarkus Framework](https://quarkus.io/guides/)
- [JWT.io](https://jwt.io/) - JWT debugging
- [BCrypt](https://en.wikipedia.org/wiki/Bcrypt) - Password hashing
- [Jakarta Bean Validation](https://jakarta.ee/specifications/bean-validation/)
- [Panache ORM](https://quarkus.io/guides/hibernate-orm-panache)

### Security
- [OWASP Authentication Guidelines](https://owasp.org/www-project-authentication-cheat-sheet/)
- [OAuth 2.0 RFC](https://tools.ietf.org/html/rfc6749)
- [JWT Best Practices](https://tools.ietf.org/html/rfc8725)

---

## 📝 Licença

Este projeto faz parte do sistema Vortex - TCC PUCRS.

---

*Última atualização: Implementação completa do serviço de autorização com 42 testes de criação de conta e 56 testes de recuperação de senha - todos os testes passando com 100% de estabilidade.*