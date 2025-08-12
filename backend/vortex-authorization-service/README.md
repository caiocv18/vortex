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
- **Keys**: RSA 2048-bit (privateKey.pem / publicKey.pem)

## 🧪 Testes

### Estratégia Implementada
- **42 testes** focados no fluxo de criação de conta
- **3 categorias**: Unitários, Integração e Validação
- **Environment isolado** sem dependências externas
- **BCrypt testing** com configuração de 12 rounds

### Execução dos Testes
```bash
# Script especializado (recomendado)
./scripts/run-auth-registration-tests.sh --all

# Testes unitários apenas
./scripts/run-auth-registration-tests.sh --unit

# Com cobertura de código
./scripts/run-auth-registration-tests.sh --coverage

# Modo watch para desenvolvimento
./scripts/run-auth-registration-tests.sh --watch

# Maven tradicional
mvn test
```

### Cobertura de Testes
- **SimplePasswordServiceTest**: 24 testes (validação e criptografia)
- **ValidationTest**: 10 testes (DTOs com Bean Validation)
- **SimpleAuthServiceTest**: 8 testes (estruturas e TestDataBuilder)

## 🔧 Configuração

### Variáveis de Ambiente
```bash
# Database
QUARKUS_DATASOURCE_USERNAME=vortex_auth
QUARKUS_DATASOURCE_PASSWORD=secure_password
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/vortex_auth

# JWT
JWT_PRIVATE_KEY_LOCATION=/path/to/privateKey.pem
JWT_PUBLIC_KEY_LOCATION=/path/to/publicKey.pem

# Password Policy
AUTH_PASSWORD_MIN_LENGTH=8
AUTH_PASSWORD_MAX_LENGTH=128
AUTH_PASSWORD_REQUIRE_UPPERCASE=true
AUTH_PASSWORD_REQUIRE_LOWERCASE=true
AUTH_PASSWORD_REQUIRE_NUMBERS=true
AUTH_PASSWORD_REQUIRE_SPECIAL_CHARS=true

# Rate Limiting
RATE_LIMIT_LOGIN_MAX_ATTEMPTS=5
RATE_LIMIT_LOGIN_WINDOW_MINUTES=15
RATE_LIMIT_REGISTER_MAX_ATTEMPTS=3
RATE_LIMIT_REGISTER_WINDOW_MINUTES=60
```

### Profiles Disponíveis
- `dev` - H2 database, logging debug
- `test` - H2 in-memory, ideal para testes
- `postgres` - PostgreSQL, produção local
- `prod` - PostgreSQL, configurações de produção

### Messaging Configuration
```yaml
# application.properties
mp.messaging.outgoing.auth-events.connector=smallrye-kafka
mp.messaging.outgoing.auth-events.topic=vortex.auth.events
mp.messaging.outgoing.auth-events.value.serializer=org.apache.kafka.common.serialization.StringSerializer
```

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

### Logging
```yaml
# Structured logging em JSON
quarkus.log.console.json=true
quarkus.log.level=INFO
quarkus.log.category."br.com.vortex.authorization".level=DEBUG
```

## 🔒 Segurança

### Rate Limiting
Implementado para prevenir ataques:
- **Login**: 5 tentativas por 15 minutos
- **Register**: 3 tentativas por hora  
- **Password Reset**: 2 tentativas por hora

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
- [ ] JaCoCo plugin para coverage reports
- [ ] Performance benchmarks
- [ ] Load testing com Gatling
- [ ] Security scanning com OWASP ZAP
- [ ] Contract testing com Pact

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
```bash
# Gerar chaves RSA
mkdir -p src/main/resources
cd src/main/resources

# Private key
openssl genrsa -out privateKey.pem 2048

# Public key
openssl rsa -pubout -in privateKey.pem -out publicKey.pem
```

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

*Última atualização: Implementação completa do serviço de autorização com 42 testes focados no fluxo de criação de conta.*