# 🚀 Vortex Launcher

Script de inicialização completo para o sistema Vortex que gerencia automaticamente backend (Spring Boot) e frontend (Vue.js/Vite) em diferentes ambientes.

## 📋 Características

- **🔧 Ambiente de Desenvolvimento**: H2 Database + Hot Reload
- **🚀 Ambiente de Produção**: Oracle Database + Build Otimizado
- **🎨 Frontend Integrado**: Vue.js com Vite dev server ou build para produção
- **🐳 Docker Completo**: Suporte total para containerização
- **⚡ Detecção Automática**: Verifica pré-requisitos e adapta execução
- **📊 Status em Tempo Real**: Monitoramento de serviços
- **🛑 Gerenciamento Completo**: Start, stop e limpeza de ambiente

## 🚀 Uso Rápido

### Modo Interativo (Recomendado)
```bash
./start-vortex.sh
```

### Modo Direto
```bash
# Ambiente de desenvolvimento
./start-vortex.sh -e dev

# Ambiente de produção
./start-vortex.sh -e prd

# Apenas backend
./start-vortex.sh -e dev --backend-only

# Apenas frontend
./start-vortex.sh -e dev --frontend-only

# Com logs
./start-vortex.sh -e prd --logs
```

### Gerenciamento
```bash
# Parar todos os serviços
./start-vortex.sh --stop

# Limpar ambiente (cuidado: apaga dados!)
./start-vortex.sh --clean
```

## 🌍 Ambientes

### 🔧 Desenvolvimento (dev)
- **Backend**: Spring Boot com profile `dev`
- **Database**: H2 em memória
- **Frontend**: Vite dev server (hot reload)
- **Execução**: Local (Maven) ou Docker
- **URLs**:
  - API: http://localhost:8080
  - Frontend: http://localhost:5173
  - H2 Console: http://localhost:8080/h2-console

### 🚀 Produção (prd)
- **Backend**: Spring Boot com profile `prd`
- **Database**: Oracle Database (Docker)
- **Frontend**: Build otimizado + Nginx
- **Execução**: Docker Compose
- **URLs**:
  - API: http://localhost:8080
  - Frontend: http://localhost:3000 (Docker) ou http://localhost:4173 (local)
  - Oracle: localhost:1521

## 📦 Componentes

### Backend (Spring Boot)
- **Dev**: Maven local ou Docker
- **Prd**: Docker com Oracle
- **Profiles**: Automático baseado no ambiente
- **Logs**: `backend.log` (dev) ou `docker logs vortex-app` (prd)

### Frontend (Vue.js/Vite)
- **Dev**: `npm run dev` (se npm disponível)
- **Prd**: `npm run build` + `npm run preview` ou Docker + Nginx
- **Fallback**: Docker automático se npm não disponível
- **Logs**: `frontend.log` ou `docker logs vortex-frontend`

### Database
- **Dev**: H2 em memória (automático)
- **Prd**: Oracle 19c Enterprise (Docker)
- **Dados**: Scripts de inicialização automática
- **Persistência**: Volume Docker para Oracle

## 🔧 Pré-requisitos

### Obrigatórios
- ✅ Docker
- ✅ Docker Compose

### Opcionais (para melhor experiência)
- 🔶 Node.js + npm (frontend local)
- 🔶 Maven (backend dev local)
- 🔶 Java 24 (backend dev local)

## 📊 Detecção Automática

O script detecta automaticamente:

1. **Docker**: Verifica instalação e status
2. **Node.js/npm**: Para execução local do frontend
3. **Maven**: Para execução local do backend
4. **Serviços Ativos**: Evita conflitos de porta
5. **Dependências**: Instala automaticamente quando possível

## 🎯 Estratégias de Execução

### Desenvolvimento Local Ideal
```
✅ Node.js + npm disponível
✅ Maven disponível
→ Backend: Maven local (hot reload)
→ Frontend: Vite dev server (hot reload)
→ Database: H2 em memória
```

### Desenvolvimento Docker
```
❌ Node.js/Maven não disponível
→ Backend: Docker
→ Frontend: Docker (se necessário)
→ Database: H2 em memória
```

### Produção Completa
```
→ Backend: Docker + Oracle
→ Frontend: Docker + Nginx (se npm não disponível)
→ Frontend: Build local + preview (se npm disponível)
→ Database: Oracle Enterprise
```

## 📋 Comandos Úteis

### Durante Execução
```bash
# Status dos serviços
docker ps

# Logs em tempo real
docker logs vortex-app -f        # Backend (prd)
docker logs vortex-db -f         # Oracle
tail -f backend.log              # Backend (dev)
tail -f frontend.log             # Frontend

# Acessar Oracle
docker exec -it vortex-db sqlplus system/Oracle_1234@ORCLPDB1
```

### Desenvolvimento
```bash
# Apenas Oracle para dev local
cd backend && docker-compose up db -d

# Backend dev local
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend dev local
cd frontend && npm run dev
```

## 🐛 Troubleshooting

### Problemas Comuns

**1. Porta 8080 em uso**
```bash
./start-vortex.sh --stop
# ou
lsof -ti:8080 | xargs kill -9
```

**2. Oracle não inicia**
```bash
# Verificar recursos Docker (mín. 2GB RAM)
docker system info

# Limpar e reiniciar
./start-vortex.sh --clean
```

**3. Frontend não carrega**
```bash
# Verificar se npm está disponível
npm --version

# Instalar dependências manualmente
cd frontend && npm install
```

**4. Problemas de permissão**
```bash
chmod +x start-vortex.sh
```

### Logs de Debug
```bash
# Backend
tail -f backend.log

# Frontend
tail -f frontend.log

# Docker
docker logs vortex-app -f
docker logs vortex-db -f
docker logs vortex-frontend -f
```

## 🔄 Fluxo de Execução

1. **Verificação**: Pré-requisitos e ambiente
2. **Seleção**: Ambiente (dev/prd) e componentes
3. **Limpeza**: Para serviços existentes
4. **Inicialização**: Serviços na ordem correta
5. **Monitoramento**: Status e saúde dos serviços
6. **Relatório**: URLs e comandos úteis

## 🎉 Recursos Avançados

- **🔄 Auto-restart**: Detecta serviços já rodando
- **📦 Fallback Docker**: Quando ferramentas locais não estão disponíveis
- **🧹 Limpeza Inteligente**: Remove apenas o necessário
- **📊 Status Real-time**: Monitora PIDs e containers
- **🎨 Interface Colorida**: Output visualmente organizado
- **⚡ Execução Paralela**: Otimizada para velocidade

## 📞 Suporte

Para problemas ou melhorias, verifique:
1. Logs dos serviços
2. Status do Docker
3. Recursos disponíveis (RAM/CPU)
4. Conflitos de porta
5. Permissões de arquivo 