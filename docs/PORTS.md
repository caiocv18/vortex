# 🚢 Esquema de Portas do Vortex

## 📋 Mapeamento de Portas

### 🔐 Serviços de Autorização
- **Frontend (React 19 + TypeScript)**: `3001`
  - Modo leve: `http://localhost:3001` (nginx estático `vortex-static`)
  - Modo dev: `http://localhost:3001` (Vite dev server)
  - Docker: `http://localhost:3001` (container `vortex-auth-frontend`, nginx na 80)
  - Build Tool: Vite 8
  - UI: Tailwind CSS 4
- **Backend (Quarkus 3.40 + Java 27)**: `8081`
  - Modo leve: `java -jar target/quarkus-app/quarkus-run.jar` com o perfil `leve`
  - Modo dev: `./mvnw quarkus:dev`
  - API Docs: `http://localhost:8081/q/swagger-ui`
  - Health: `http://localhost:8081/q/health`

### 🏢 Serviços Principais
- **Frontend (Vue.js 3.5 + TypeScript)**: `5173` (leve e dev) / `4173` (preview, modo dev com prd) / `3000` (Docker)
  - Modo leve: `http://localhost:5173` (nginx estático `vortex-static`)
  - Modo dev: `http://localhost:5173` (Vite dev server, `--strictPort`)
  - Docker: `http://localhost:3000` (container `vortex-frontend`, nginx na 80)
  - Build Tool: Vite 8
  - UI: Vuetify 4
- **Backend (Spring Boot 4.1 + Java 27)**: `8080`
  - Modo leve: jar extraído em `target/leve/` com cache AOT
  - Modo dev: `./mvnw spring-boot:run`
  - Prd: container `vortex-app`
  - API Docs: `http://localhost:8080/swagger-ui.html`
  - Health: `http://localhost:8080/health`

### 🌐 Servidor estático do modo leve
- **vortex-static** (`nginx:1.30.6-alpine`, `infra/docker/docker-compose.leve.yml`): `5173` e `3001`
  - Serve os `dist/` dos dois frontends, com fallback de SPA para o `index.html`
  - Sem proxy de `/api/`: o navegador chama os backends direto em `8080` e `8081`

### 📊 Infraestrutura
- **PostgreSQL** (`postgres:18.6-alpine`): `5432` (dados do serviço de autorização)
- **Kafka** (`apache/kafka-native:4.4.0`, KRaft): `9092` (host) / `kafka:19092` (rede Docker). Não há mais ZooKeeper (a `2181` saiu)
- **Kafka UI** (`kafbat/kafka-ui:v1.5.0`): `8090`, só com o profile `ui` (`--profile ui up -d kafka-ui`)
- **Schema Registry** (profile `schema-registry` do `docker-compose.kafka.yml`): `8085` no host (dentro da rede continua `8081`)
- **Kafka Connect** (profile `connect`): `8083`
- **ksqlDB** (profile `ksqldb`): `8088`
- **RabbitMQ** (`rabbitmq:4.4.0-management-alpine`): `5672` (AMQP) / `15672` (Management)
- **Oracle Database Free** (`gvenzl/oracle-free:23.26.3-slim-faststart`): `1521`, serviço `FREEPDB1`
- **H2 Console**: `8080/h2-console` (apenas ambiente dev)

## 🔄 Fluxo de Autenticação

1. **Acesso Inicial**: `http://localhost:5173` → Vue.js verifica autenticação
2. **Redirecionamento**: Se não autenticado → `http://localhost:3001/login`
3. **Login**: React frontend se comunica com `http://localhost:8081/api/auth`
4. **Autenticação**: Quarkus backend valida credenciais e gera JWT
5. **Callback**: Retorna para `http://localhost:5173` com tokens JWT
6. **Sessão Ativa**: Vue.js armazena tokens e acessa `http://localhost:8080/api`
7. **Refresh**: Tokens são renovados automaticamente via `http://localhost:8081/api/auth/refresh`

## 🛠️ Comandos para Iniciar

```bash
# Iniciar todos os serviços (auth + main), modo interativo
./start-vortex.sh

# Modo leve (padrão), sem mensageria
./start-vortex.sh --modo leve -e dev -m none

# Modo dev (hot reload) com Kafka
./start-vortex.sh -e dev -r dev -m kafka

# Iniciar apenas a aplicação principal (o serviço de autorização sobe sempre)
./start-vortex.sh --backend-only

# Parar todos os serviços
./start-vortex.sh --stop
```

## 🔍 Verificação de Conflitos

O `start-vortex.sh` roda `scripts/check-ports.sh --auto-kill` na subida. O script verifica `3001`, `5173`, `8080`, `8081`, `9092` e `5432`, só considera quem está **escutando** na porta, pula as portas publicadas por containers Docker e manda TERM antes do KILL.

```bash
# Verificar portas em uso (todas as portas do sistema)
ss -ltn | grep -E ':(3001|5173|4173|3000|8080|8081|8083|8085|8088|8090|9092|5672|15672|1521|5432)\b'

# Verificação individual por serviço (só quem escuta)
lsof -iTCP:3001 -sTCP:LISTEN   # Auth Frontend (React)
lsof -iTCP:5173 -sTCP:LISTEN   # Main Frontend (Vue.js)
lsof -iTCP:4173 -sTCP:LISTEN   # Main Frontend Prod Preview
lsof -iTCP:8080 -sTCP:LISTEN   # Main Backend (Spring Boot)
lsof -iTCP:8081 -sTCP:LISTEN   # Auth Backend (Quarkus)

# Portas de containers (PostgreSQL, Kafka, RabbitMQ, Oracle, vortex-static)
docker ps --format 'table {{.Names}}\t{{.Ports}}'
```

## ⚠️ Problemas Conhecidos

- **Loop de Redirecionamento**: Corrigido com processamento adequado de parâmetros URL
- **Conflitos de Porta**: Cada serviço tem porta única e bem definida
- **CORS**: Configurado para permitir comunicação entre todos os serviços
- **JWT Token Expiry**: Tokens são renovados automaticamente via refresh token
- **Database Connection**: PostgreSQL para auth service, Oracle para main application
- **Service Dependencies**: Auth service deve estar ativo para main application funcionar
- **Schema Registry**: Saiu da 8081 (que é do auth) para a 8085 no host

## 🚀 Resumo de Serviços

| Serviço | Tecnologia | Porta | Ambiente | Função |
|---------|------------|-------|----------|---------|
| Auth Frontend | React 19 + TS + Vite 8 | 3001 | Dev/Prod | Interface de autenticação |
| Auth Backend | Quarkus 3.40 + Java 27 | 8081 | Dev/Prod | API de autenticação/autorização |
| Main Frontend | Vue.js 3.5 + TS + Vite 8 | 5173/4173/3000 | Dev/Prod | Interface principal do sistema |
| Main Backend | Spring Boot 4.1 + Java 27 | 8080 | Dev/Prod | API principal do negócio |
| vortex-static | nginx 1.30.6 | 5173 e 3001 | Modo leve | Serve os dois frontends compilados |
| PostgreSQL | PostgreSQL 18.6 | 5432 | Dev/Prod | Dados de autenticação |
| Oracle | Oracle Database Free 23ai | 1521 | Prod | Dados da aplicação |
| H2 Console | Database | 8080/h2-console | Dev | Console de desenvolvimento |
| Kafka | Kafka 4.4 nativo (KRaft) | 9092 | Dev/Prod | Mensageria assíncrona |
| Kafka UI | kafbat/kafka-ui | 8090 | Profile `ui` | Interface web do Kafka |
| Schema Registry | Confluent 8.4 | 8085 | Profile `schema-registry` | Registro de schemas |
| RabbitMQ | RabbitMQ 4.4 | 5672/15672 | Dev/Prod | Mensageria alternativa |
