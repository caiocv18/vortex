# Prompt para Integração dos Serviços Vortex

Preciso integrar o serviço de autorização (`vortex-authorization-service`) com o serviço principal da aplicação (`vortex-application-service`) seguindo uma arquitetura de microserviços com as seguintes especificações:

## Arquitetura da Integração

### 1. **Padrão de Integração**: Authentication Service
- O `vortex-authorization-service` deve funcionar como um serviço de autenticação centralizado
- O `vortex-application-service` deve validar tokens JWT fornecidos pelo authorization service
- Implementar middleware de autenticação no application service para proteger endpoints

### 2. **Fluxo de Autenticação**: Login Unificado
- Integrar os componentes React de login/registro do `vortex-authorization-service` no frontend Vue.js principal
- O usuário faz login através do frontend principal (`vortex-application-service`)
- Após autenticação, o usuário permanece no frontend principal com acesso às funcionalidades protegidas
- Implementar gerenciamento de estado de autenticação no frontend Vue.js

### 3. **Comunicação entre Serviços**: REST API Síncrona
- O frontend Vue.js se comunica com o `vortex-authorization-service` via REST para login/registro
- O `vortex-application-service` valida tokens JWT fazendo chamadas REST para o authorization service
- Implementar client HTTP no backend Spring Boot para comunicação entre serviços

### 4. **Estratégia de Banco de Dados**: Bancos Separados
- Manter bancos de dados independentes para cada serviço
- `vortex-authorization-service` mantém seu próprio banco H2 para usuários
- `vortex-application-service` mantém seu banco H2 para dados de negócio
- Não compartilhar esquemas ou tabelas entre os serviços

### 5. **Estratégia de Frontend**: Unificado
- Integrar os componentes React (`LoginPage`, `RegisterPage`, `DashboardPage`) no frontend Vue.js
- Criar wrappers Vue.js para os componentes React ou convertê-los para Vue.js
- Manter uma única aplicação frontend com roteamento unificado
- Implementar guards de rota baseados no estado de autenticação

### 6. **Configuração**: Híbrida
- Estender o `start-vortex.sh` para inicializar ambos os serviços
- Manter configurações específicas em cada serviço (`application.properties`)
- Criar configurações compartilhadas para URLs de integração e CORS
- Configurar portas diferentes para cada serviço (auth: 8081, app: 8080)

### 7. **Segurança**: JWT Simples
- Aproveitar o `JwtService` já implementado no `vortex-authorization-service`
- Implementar validação de JWT no `vortex-application-service`
- Proteger endpoints críticos do application service com middleware de autenticação
- Configurar CORS adequadamente para comunicação entre frontends

### 8. **Roteamento**: Application-Level
- Implementar roteamento no nível da aplicação Spring Boot
- Configurar Vue Router para incluir rotas de autenticação
- Criar interceptors HTTP para adicionar tokens JWT automaticamente
- Implementar redirecionamento automático para login quando não autenticado

## Requisitos Técnicos

### Backend
- Criar filtro de autenticação JWT no `vortex-application-service`
- Implementar client REST para comunicação com o authorization service
- Adicionar endpoints de validação de token
- Configurar CORS para permitir comunicação entre serviços

### Frontend
- Integrar componentes de autenticação no Vue.js principal
- Implementar store Pinia para gerenciamento de estado de autenticação
- Criar guards de rota para proteger páginas que requerem autenticação
- Configurar interceptors Axios para incluir tokens JWT

### Configuração
- Atualizar `start-vortex.sh` para inicializar ambos os serviços
- Configurar variáveis de ambiente para URLs dos serviços
- Adicionar profiles Spring Boot para diferentes ambientes
- Configurar proxy de desenvolvimento no Vite para evitar problemas de CORS

### Testes
- Criar testes de integração para fluxo de autenticação
- Testar comunicação entre serviços
- Validar proteção de endpoints
- Testar fluxo completo de login/logout

---

## Arquivos Essenciais para Implementação

### Backend - Authorization Service
@backend/vortex-authorization-service/src/main/java/br/com/vortex/login/service/JwtService.java
@backend/vortex-authorization-service/src/main/java/br/com/vortex/login/service/AuthService.java
@backend/vortex-authorization-service/src/main/java/br/com/vortex/login/resource/AuthResource.java
@backend/vortex-authorization-service/src/main/java/br/com/vortex/login/dto/LoginResponseDTO.java
@backend/vortex-authorization-service/src/main/java/br/com/vortex/login/dto/UserDTO.java
@backend/vortex-authorization-service/src/main/resources/application.properties

### Backend - Application Service
@backend/vortex-application-service/src/main/java/br/com/vortex/application/config/CorsConfig.java
@backend/vortex-application-service/src/main/java/br/com/vortex/application/controller/ProdutoController.java
@backend/vortex-application-service/src/main/java/br/com/vortex/application/controller/MovimentoController.java
@backend/vortex-application-service/src/main/resources/application.properties
@backend/vortex-application-service/pom.xml

### Frontend - Authorization Service
@frontend/vortex-authorization-service/src/pages/LoginPage.tsx
@frontend/vortex-authorization-service/src/pages/RegisterPage.tsx
@frontend/vortex-authorization-service/src/pages/DashboardPage.tsx
@frontend/vortex-authorization-service/src/services/authService.ts
@frontend/vortex-authorization-service/src/hooks/useAuth.tsx

### Frontend - Application Service
@frontend/vortex-application-service/src/App.vue
@frontend/vortex-application-service/src/router/index.ts
@frontend/vortex-application-service/src/stores/counter.ts
@frontend/vortex-application-service/src/api/config.ts
@frontend/vortex-application-service/vite.config.ts
@frontend/vortex-application-service/package.json

### Configuração e Scripts
@start-vortex.sh
@CLAUDE.md