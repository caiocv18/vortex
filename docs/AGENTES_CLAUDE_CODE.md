# 🤖 Agentes Especializados para Claude Code - Projeto VORTEX

Este documento define agentes especializados recomendados para acelerar o desenvolvimento do TCC do Sistema de Controle de Estoque VORTEX usando Claude Code.

## 📋 Visão Geral

O projeto VORTEX já possui uma base arquitetural sólida com:
- **Microserviços**: 2 backends (Spring Boot + Quarkus) + 2 frontends (Vue.js + React)
- **Event-Driven**: Suporte a Kafka, RabbitMQ e SQS
- **Multi-Database**: Oracle, PostgreSQL, H2
- **Type-Safe**: APIs auto-geradas via OpenAPI

Os agentes propostos visam adicionar camadas de **qualidade**, **segurança** e **observabilidade** ao sistema existente.

---

## 🎯 **AGENTES RECOMENDADOS**

### **1. 🔒 AGENTE DE SEGURANÇA (CRÍTICO)**
```yaml
Nome: security-enhancement-agent
Descrição: Implementar autenticação, autorização e hardening de segurança
Contexto: Sistema atualmente sem autenticação robusta
```

**Responsabilidades:**
- [ ] Integrar Spring Security com JWT no backend principal
- [ ] Configurar CORS adequado por ambiente (dev/prd)
- [ ] Implementar rate limiting e proteção contra ataques
- [ ] Auditoria de segurança em todos os endpoints
- [ ] Validação robusta de inputs com Bean Validation
- [ ] Sanitização de logs para evitar vazamento de dados
- [ ] Implementar refresh tokens e logout seguro
- [ ] HTTPS enforcement e security headers

**Arquivos Alvo:**
- `backend/vortex-application-service/src/main/java/br/com/vortex/application/config/security/`
- `backend/vortex-authorization-service/src/main/java/br/com/vortex/authorization/security/`
- Arquivos de configuração `application*.properties`

---

### **2. 🧪 AGENTE DE TESTES (CRÍTICO)**
```yaml
Nome: test-coverage-agent
Descrição: Criar suite completa de testes automatizados
Contexto: Cobertura atual próxima de 0%, apenas alguns testes E2E
```

**Responsabilidades:**
- [ ] Testes unitários para todos os services (meta: >80% cobertura)
- [ ] Testes de integração com H2/TestContainers
- [ ] Mocks para message brokers (Kafka/RabbitMQ/SQS)
- [ ] Testes E2E completos com Playwright
- [ ] Performance/load testing com JMeter/Gatling
- [ ] CI/CD pipeline com validação automática de qualidade
- [ ] Mutation testing para validar qualidade dos testes
- [ ] Contract testing entre microserviços

**Arquivos Alvo:**
- `backend/*/src/test/java/`
- `frontend/*/e2e/`
- `.github/workflows/` (CI/CD)
- Configurações de cobertura e qualidade

---

### **3. 📨 AGENTE DE MENSAGERIA**
```yaml
Nome: messaging-optimization-agent
Descrição: Otimizar e expandir sistema de mensageria
Contexto: Sistema já possui factory pattern para múltiplos brokers
```

**Responsabilidades:**
- [ ] Implementar Dead Letter Queues (DLQ) para todos os brokers
- [ ] Retry policies com backoff exponencial
- [ ] Monitoring e métricas de filas em tempo real
- [ ] Schema Registry para Kafka (Avro/JSON Schema)
- [ ] Particionamento inteligente baseado em tenant/região
- [ ] Tratamento de eventos duplicados (idempotência)
- [ ] Event sourcing para auditoria completa
- [ ] Saga pattern para transações distribuídas

**Arquivos Alvo:**
- `backend/vortex-application-service/src/main/java/br/com/vortex/application/service/*Service.java`
- `backend/vortex-application-service/src/main/java/br/com/vortex/application/factory/`
- Arquivos de configuração de messaging

---

### **4. 📊 AGENTE DE OBSERVABILIDADE**
```yaml
Nome: observability-agent
Descrição: Implementar monitoring, logging e métricas
Contexto: Sistema precisa de visibilidade operacional
```

**Responsabilidades:**
- [ ] Integração com Micrometer/Prometheus
- [ ] Logs estruturados com ELK Stack/OpenTelemetry
- [ ] Health checks avançados com dependências
- [ ] Circuit breakers com Resilience4j
- [ ] Dashboards Grafana para métricas de negócio
- [ ] Alertas proativos baseados em SLA
- [ ] Distributed tracing entre microserviços
- [ ] APM (Application Performance Monitoring)

**Arquivos Alvo:**
- `backend/*/src/main/java/*/config/`
- Arquivos de configuração de observabilidade
- `infra/monitoring/` (novo)

---

### **5. ⚡ AGENTE DE PERFORMANCE**
```yaml
Nome: performance-optimization-agent
Descrição: Otimização de performance e escalabilidade
Contexto: Preparar sistema para alta demanda
```

**Responsabilidades:**
- [ ] Implementar cache Redis/Caffeine em múltiplas camadas
- [ ] Otimizar queries JPA com projections e índices
- [ ] Async processing com @Async e thread pools
- [ ] Connection pooling otimizado (HikariCP)
- [ ] Bundle optimization no frontend (tree shaking, code splitting)
- [ ] Lazy loading de componentes Vue/React
- [ ] CDN para assets estáticos
- [ ] Database query optimization e indexing strategy

**Arquivos Alvo:**
- `backend/*/src/main/java/*/repository/`
- `backend/*/src/main/java/*/service/`
- `frontend/*/src/` (otimizações de build)
- Configurações de cache e performance

---

### **6. 🚀 AGENTE DE DevOps/INFRAESTRUTURA**
```yaml
Nome: devops-infrastructure-agent
Descrição: Containerização e deploy automation
Contexto: Sistema possui Dockerfiles básicos, precisa de orquestração
```

**Responsabilidades:**
- [ ] Multi-stage Dockerfiles otimizados para produção
- [ ] Kubernetes manifests/Helm charts completos
- [ ] CI/CD com GitHub Actions (build, test, deploy)
- [ ] Environment promotion pipeline (dev → staging → prod)
- [ ] Blue-green deployment strategy
- [ ] Backup automatizado e disaster recovery
- [ ] Infrastructure as Code (Terraform/Pulumi)
- [ ] Service mesh (Istio) para microserviços

**Arquivos Alvo:**
- `infra/` (expandir estrutura existente)
- `.github/workflows/`
- `k8s/` (novo)
- Dockerfiles existentes

---

### **7. 📚 AGENTE DE DOCUMENTAÇÃO TCC**
```yaml
Nome: academic-documentation-agent
Descrição: Documentação acadêmica e técnica
Contexto: Preparar documentação para defesa de TCC
```

**Responsabilidades:**
- [ ] Documentação arquitetural completa (C4 Model)
- [ ] Diagramas UML/ERD atualizados e detalhados
- [ ] Análise de requisitos funcionais e não funcionais
- [ ] Benchmarks de performance com resultados
- [ ] Análise comparativa de tecnologias escolhidas
- [ ] Relatório de lessons learned e decisões técnicas
- [ ] Documentação de APIs com OpenAPI 3.0
- [ ] Guias de instalação e deployment

**Arquivos Alvo:**
- `docs/` (expandir estrutura existente)
- `docs/architecture/` (novo)
- `docs/tcc/` (novo)
- READMEs de cada componente

---

### **8. 🎨 AGENTE DE UI/UX**
```yaml
Nome: frontend-enhancement-agent
Descrição: Melhorar experiência do usuário
Contexto: Frontends funcionais precisam de polimento
```

**Responsabilidades:**
- [ ] Design system consistente entre aplicações
- [ ] Componentes acessíveis (WCAG/ARIA compliance)
- [ ] Progressive Web App (PWA) capabilities
- [ ] Responsive design otimizado para mobile
- [ ] State management avançado com Pinia/Zustand
- [ ] Internacionalização (i18n) pt-BR/en-US
- [ ] Dark/light theme com persistência
- [ ] Skeleton loading e estados de carregamento

**Arquivos Alvo:**
- `frontend/vortex-application-service/src/`
- `frontend/vortex-authorization-service/src/`
- Arquivos de tema e configuração UI

---

### **9. 📈 AGENTE DE RELATÓRIOS AVANÇADOS**
```yaml
Nome: analytics-reporting-agent
Descrição: BI e relatórios gerenciais avançados
Contexto: Sistema possui relatórios básicos, expandir capacidades
```

**Responsabilidades:**
- [ ] Dashboard em tempo real com WebSockets
- [ ] Exportação PDF/Excel com templates profissionais
- [ ] Gráficos interativos (D3.js/Chart.js avançados)
- [ ] Filtros avançados e drill-down capabilities
- [ ] Scheduling de relatórios via email
- [ ] Machine learning para previsões de estoque
- [ ] Data warehouse com agregações otimizadas
- [ ] KPIs e métricas de negócio automatizadas

**Arquivos Alvo:**
- `backend/vortex-application-service/src/main/java/*/service/RelatorioService.java`
- `frontend/vortex-application-service/src/views/ReportsView.vue`
- `backend/*/src/main/java/*/service/AnalyticsService.java`

---

### **10. 🔄 AGENTE DE MIGRAÇÃO DE DADOS**
```yaml
Nome: data-migration-agent
Descrição: Versionamento e migração de banco de dados
Contexto: Sistema usa import.sql, precisa de migrations robustas
```

**Responsabilidades:**
- [ ] Scripts Flyway/Liquibase para versionamento
- [ ] Migrations seguras entre ambientes
- [ ] Data seeding automatizado por ambiente
- [ ] Backup/restore procedures automatizados
- [ ] Schema evolution strategy
- [ ] Data quality validation e integrity checks
- [ ] Zero-downtime deployments com migrations
- [ ] Rollback strategy para mudanças críticas

**Arquivos Alvo:**
- `backend/*/src/main/resources/db/migration/` (novo)
- Arquivos `import.sql` existentes
- Scripts de backup e restore

---

## 🎯 **ORDEM DE PRIORIDADE PARA TCC**

### **Fase 1 - Fundação (Crítica)**
1. **🔒 Agente de Segurança** - Base crítica para qualquer sistema
2. **🧪 Agente de Testes** - Demonstra qualidade e profissionalismo
3. **📚 Agente de Documentação TCC** - Essencial para defesa acadêmica

### **Fase 2 - Qualidade (Alta Prioridade)**
4. **📊 Agente de Observabilidade** - Mostra maturidade técnica
5. **⚡ Agente de Performance** - Diferencial competitivo
6. **🚀 Agente DevOps** - Modernidade e best practices

### **Fase 3 - Diferenciação (Média Prioridade)**
7. **📨 Agente de Mensageria** - Aproveita arquitetura existente
8. **🎨 Agente de UI/UX** - Polimento da experiência
9. **📈 Agente de Relatórios Avançados** - Funcionalidades de negócio

### **Fase 4 - Robustez (Baixa Prioridade)**
10. **🔄 Agente de Migração de Dados** - Operações avançadas

---

## 📝 **COMO USAR ESTE DOCUMENTO**

1. **Escolha um agente** baseado na prioridade e necessidade atual
2. **Configure o Claude Code** com o contexto específico do agente
3. **Use as responsabilidades** como checklist para o desenvolvimento
4. **Focalize nos arquivos alvo** para manter escopo definido
5. **Documente progresso** atualizando este arquivo

---

## 🔗 **INTEGRAÇÃO COM PROJETO EXISTENTE**

Este documento complementa:
- `/CLAUDE.md` - Comandos e configurações do projeto
- `/docs/prompts/ANALISE_MELHORIAS_VORTEX.md` - Análise técnica detalhada
- `/docs/authentication/README.md` - Documentação de autenticação
- `/README.md` - Visão geral do projeto

---

**Criado em:** 2025-01-09  
**Última atualização:** 2025-01-09  
**Versão:** 1.0.0