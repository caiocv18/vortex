# 🚢 Esquema de Portas do Vortex

## 📋 Mapeamento de Portas

### 🔐 Serviços de Autorização
- **Frontend (React + TypeScript)**: `3001`
  - Dev: `http://localhost:3001`
  - Prod: `http://localhost:3001` (Docker)
  - Build Tool: Vite
  - UI: TailwindCSS
- **Backend (Quarkus + Java 17)**: `8081`
  - Dev: `http://localhost:8081`
  - Prod: `http://localhost:8081` (Docker)
  - API Docs: `http://localhost:8081/q/swagger-ui`
  - Health: `http://localhost:8081/q/health`

### 🏢 Serviços Principais  
- **Frontend (Vue.js 3 + TypeScript)**: `5173` (dev) / `4173` (prod)
  - Dev: `http://localhost:5173`
  - Prod: `http://localhost:4173` (preview) / `3000` (Docker)
  - Build Tool: Vite
  - UI: Vuetify 3
- **Backend (Spring Boot + Java 24)**: `8080`
  - Dev: `http://localhost:8080`
  - Prod: `http://localhost:8080` (Docker)
  - API Docs: `http://localhost:8080/swagger-ui.html`
  - Health: `http://localhost:8080/health`

### 📊 Infraestrutura
- **Kafka**: `9092` (broker) / `2181` (zookeeper)
- **Kafka UI**: `8090`
- **RabbitMQ**: `5672` (AMQP) / `15672` (Management)
- **Oracle DB**: `1521` (main application data)
- **PostgreSQL**: `5433` (authentication service data)
- **H2 Console**: `8080/h2-console` (apenas dev)

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
# Iniciar todos os serviços (auth + main)
./start-vortex.sh

# Iniciar apenas desenvolvimento com autenticação
./start-vortex.sh -e dev -m kafka

# Iniciar apenas serviços de autenticação
./start-vortex.sh --auth-only

# Iniciar apenas aplicação principal
./start-vortex.sh --backend-only

# Parar todos os serviços
./start-vortex.sh --stop
```

## 🔍 Verificação de Conflitos

```bash
# Verificar portas em uso (todas as portas do sistema)
netstat -tuln | grep -E ':(3001|5173|4173|3000|8080|8081|9092|2181|8090|5672|15672|1521|5433)'

# Verificação individual por serviço
lsof -i :3001  # Auth Frontend (React)
lsof -i :5173  # Main Frontend Dev (Vue.js)
lsof -i :4173  # Main Frontend Prod Preview
lsof -i :3000  # Main Frontend Docker
lsof -i :8080  # Main Backend (Spring Boot)
lsof -i :8081  # Auth Backend (Quarkus)
lsof -i :9092  # Kafka Broker
lsof -i :2181  # Zookeeper
lsof -i :8090  # Kafka UI
lsof -i :5672  # RabbitMQ AMQP
lsof -i :15672 # RabbitMQ Management
lsof -i :1521  # Oracle Database
lsof -i :5433  # PostgreSQL Database
```

## ⚠️ Problemas Conhecidos

- **Loop de Redirecionamento**: Corrigido com processamento adequado de parâmetros URL
- **Conflitos de Porta**: Cada serviço tem porta única e bem definida
- **CORS**: Configurado para permitir comunicação entre todos os serviços
- **JWT Token Expiry**: Tokens são renovados automaticamente via refresh token
- **Database Connection**: PostgreSQL para auth service, Oracle para main application
- **Service Dependencies**: Auth service deve estar ativo para main application funcionar

## 🚀 Resumo de Serviços

| Serviço | Tecnologia | Porta | Ambiente | Função |
|---------|------------|-------|----------|---------|
| Auth Frontend | React + TS + Vite | 3001 | Dev/Prod | Interface de autenticação |
| Auth Backend | Quarkus + Java 17 | 8081 | Dev/Prod | API de autenticação/autorização |
| Main Frontend | Vue.js 3 + TS + Vite | 5173/4173/3000 | Dev/Prod | Interface principal do sistema |
| Main Backend | Spring Boot + Java 24 | 8080 | Dev/Prod | API principal do negócio |
| PostgreSQL | Database | 5433 | Prod | Dados de autenticação |
| Oracle | Database | 1521 | Prod | Dados da aplicação |
| H2 Console | Database | 8080/h2-console | Dev | Console de desenvolvimento |
| Kafka | Message Broker | 9092 | Dev/Prod | Mensageria assíncrona |
| Kafka UI | Web Interface | 8090 | Dev/Prod | Interface web do Kafka |
| RabbitMQ | Message Broker | 5672/15672 | Dev/Prod | Mensageria alternativa |