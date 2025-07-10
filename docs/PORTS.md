# 🚢 Esquema de Portas do Vortex

## 📋 Mapeamento de Portas

### 🔐 Serviços de Autorização
- **Frontend (React)**: `3001`
  - Dev: `http://localhost:3001`
  - Prod: `http://localhost:3001`
- **Backend (Quarkus)**: `8081`
  - Dev: `http://localhost:8081`
  - Prod: `http://localhost:8081`

### 🏢 Serviços Principais
- **Frontend (Vue.js)**: `5173` (dev) / `4173` (prod)
  - Dev: `http://localhost:5173`
  - Prod: `http://localhost:4173`
- **Backend (Spring Boot)**: `8080`
  - Dev: `http://localhost:8080`
  - Prod: `http://localhost:8080`

### 📊 Infraestrutura
- **Kafka**: `9092`
- **Kafka UI**: `8090`
- **RabbitMQ**: `5672` (AMQP) / `15672` (Management)
- **Oracle DB**: `1521`
- **H2 Console**: `8080/h2-console` (apenas dev)

## 🔄 Fluxo de Autenticação

1. **Acesso Inicial**: `http://localhost:5173` → Redireciona para `http://localhost:3001/login`
2. **Login**: `http://localhost:3001/login` → Autentica via `http://localhost:8081`
3. **Sucesso**: Redireciona para `http://localhost:5173` com dados de auth
4. **Aplicação**: Usuário logado em `http://localhost:5173`

## 🛠️ Comandos para Iniciar

```bash
# Iniciar todos os serviços
./start-vortex.sh

# Iniciar apenas desenvolvimento
./start-vortex.sh -e dev -m kafka

# Parar todos os serviços
./start-vortex.sh --stop
```

## 🔍 Verificação de Conflitos

```bash
# Verificar portas em uso
netstat -tuln | grep -E ':(3001|5173|4173|8080|8081|9092|8090|5672|15672|1521)'

# Ou usando lsof
lsof -i :3001 # Auth Frontend
lsof -i :5173 # Main Frontend Dev
lsof -i :8080 # Main Backend
lsof -i :8081 # Auth Backend
```

## ⚠️ Problemas Conhecidos

- **Loop de Redirecionamento**: Corrigido com processamento adequado de parâmetros URL
- **Conflitos de Porta**: Cada serviço tem porta única e bem definida
- **CORS**: Configurado para permitir comunicação entre serviços