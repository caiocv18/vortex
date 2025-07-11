# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Commands

### Development Setup
```bash
# Quick start (recommended) - Starts all services
./start-vortex.sh

# Manual setup - Main application only
cd backend/vortex-application-service && ./mvnw spring-boot:run
cd frontend/vortex-application-service && npm install && npm run dev

# Complete setup with authentication - All services
cd backend/vortex-authorization-service && mvn quarkus:dev
cd frontend/vortex-authorization-service && npm install && npm run dev
cd backend/vortex-application-service && ./mvnw spring-boot:run
cd frontend/vortex-application-service && npm install && npm run dev
```

### Build Commands
```bash
# Backend - Main Application Service (Spring Boot)
cd backend/vortex-application-service
./mvnw clean install          # Build project
./mvnw spring-boot:run        # Run development server
./mvnw spring-boot:build-image # Build Docker image

# Backend - Authorization Service (Quarkus)
cd backend/vortex-authorization-service
mvn clean install            # Build project
mvn quarkus:dev               # Run development server
mvn package                   # Package application

# Frontend - Main Application (Vue.js)
cd frontend/vortex-application-service
npm run build                 # Production build
npm run type-check            # TypeScript validation
npm run lint                  # ESLint with auto-fix
npm run generate-api          # Regenerate API client from OpenAPI spec

# Frontend - Authorization (React)
cd frontend/vortex-authorization-service
npm run build                 # Production build
npm run lint                  # ESLint with auto-fix
npm run test                  # Vitest unit tests
```

### Testing Commands
```bash
# Backend - Main Application tests
cd backend/vortex-application-service
./mvnw test                   # All tests
./mvnw test -Dtest=*ControllerTest # Controller tests only

# Backend - Authorization Service tests
cd backend/vortex-authorization-service
mvn test                      # All tests

# Frontend - Main Application tests
cd frontend/vortex-application-service
npm run test:unit             # Vitest unit tests
npm run test:e2e              # Playwright E2E tests

# Frontend - Authorization tests
cd frontend/vortex-authorization-service
npm run test                  # Vitest unit tests
npm run test:coverage         # Coverage report
```

### Environment Management
```bash
# Environment options
./start-vortex.sh -e dev      # Development (H2 database)
./start-vortex.sh -e prd      # Production (Oracle database)

# Messaging systems
./start-vortex.sh -m kafka    # With Kafka messaging
./start-vortex.sh -m rabbitmq # With RabbitMQ messaging
./start-vortex.sh -m sqs      # With Amazon SQS

# Service control
./start-vortex.sh --backend-only  # Backend only
./start-vortex.sh --frontend-only # Frontend only
./start-vortex.sh --auth-only     # Authentication services only
./start-vortex.sh --stop          # Stop all services
```

## Architecture Overview

### System Structure
- **Microservices Architecture**: 4 main services (2 backend + 2 frontend)
  - Main Application Service (Spring Boot - Port 8080)
  - Authorization Service (Quarkus - Port 8081)
  - Main Frontend (Vue.js - Port 5173/4173)
  - Auth Frontend (React - Port 3001)
- **Event-Driven**: Supports Kafka, RabbitMQ, and SQS messaging
- **Multi-Database**: 
  - Oracle (production) - Main application data
  - PostgreSQL (auth service) - Authentication data
  - H2 (development) - In-memory for testing
- **Type-Safe**: Auto-generated API clients from OpenAPI specifications
- **Security**: JWT-based authentication with refresh tokens

### Backend Architecture

#### Main Application Service (Spring Boot - Java 24)
- **Pattern**: Layered architecture (Controller → Service → Repository)
- **Location**: `backend/vortex-application-service/`
- **Key Packages**:
  - `controller/`: REST endpoints for inventory management
  - `service/`: Business logic and messaging integration
  - `repository/`: JPA data access
  - `dto/`: Data transfer objects
  - `model/`: JPA entities (Produto, TipoProduto, MovimentoEstoque)
  - `config/`: Configuration classes (CORS, messaging, OpenAPI, Security)
  - `factory/`: Message broker factory pattern

#### Authorization Service (Quarkus - Java 17)
- **Pattern**: RESTful microservice with reactive programming
- **Location**: `backend/vortex-authorization-service/`
- **Key Packages**:
  - `resource/`: JAX-RS REST endpoints for authentication
  - `service/`: Authentication business logic
  - `entity/`: User, Role, Token entities with Panache
  - `dto/`: Authentication request/response objects
  - `security/`: JWT service and password handling
  - `event/`: Event-driven authentication logging
  - `config/`: Messaging and security configuration

### Frontend Architecture

#### Main Application (Vue.js 3 + TypeScript)
- **Pattern**: Component-based with centralized state management
- **Location**: `frontend/vortex-application-service/`
- **Key Directories**:
  - `views/`: Page-level components (Dashboard, Products, Reports)
  - `components/`: Reusable UI components with Vuetify
  - `stores/`: Pinia state management (auth, products, movements)
  - `api/generated/`: Auto-generated API clients from OpenAPI
  - `router/`: Vue Router with authentication guards
  - `utils/`: Authentication callback handling

#### Authorization Frontend (React 18 + TypeScript)
- **Pattern**: SPA with context-based state management
- **Location**: `frontend/vortex-authorization-service/`
- **Key Directories**:
  - `pages/`: Authentication pages (Login, Register, Reset)
  - `components/`: Reusable form components
  - `contexts/`: React Context for authentication state
  - `services/`: API integration with auth backend
  - `types/`: TypeScript type definitions

### Messaging Integration
- **Factory Pattern**: `MessageBrokerFactory` abstracts messaging systems
- **Services**: Separate producer/consumer services for each messaging system
- **Topics**: 
  - `vortex.movimento.estoque` - Inventory movements
  - `vortex.produto.events` - Product events
  - `vortex.alertas.estoque` - Stock alerts

### Configuration Management
- **Profile-Based**: Environment-specific configuration files
- **Key Files**:
  - `application.properties` - Base configuration
  - `application-dev.properties` - Development environment
  - `application-prd.properties` - Production environment
  - `application-kafka.properties` - Kafka configuration
  - `application-rabbitmq.properties` - RabbitMQ configuration

## API Structure

### Main Application Service (Port 8080)
- **Base URL**: `http://localhost:8080/api`
- **Documentation**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `/docs/openapi-backend.json`

#### Key Endpoints
- **Products**: `/api/produtos` - Full CRUD operations
- **Product Types**: `/api/tipos-produto` - Category management
- **Movements**: `/api/movimentos` - Inventory transactions
- **Reports**: `/api/relatorios/*` - Business intelligence
- **Health**: `/health` - Application health check
- **Queue Monitoring**: `/api/queue` - Message broker status

### Authorization Service (Port 8081)
- **Base URL**: `http://localhost:8081/api/auth`
- **Documentation**: `http://localhost:8081/q/swagger-ui`
- **Health**: `http://localhost:8081/q/health`

#### Authentication Endpoints
- **Login**: `POST /api/auth/login` - User authentication
- **Register**: `POST /api/auth/register` - User registration
- **Logout**: `POST /api/auth/logout` - User logout
- **Refresh**: `POST /api/auth/refresh` - Token refresh
- **Password Reset**: `POST /api/auth/forgot-password` - Password recovery
- **Reset Password**: `POST /api/auth/reset-password` - Password reset

### Frontend API Integration
- **Auto-Generated**: API clients generated from OpenAPI spec
- **Command**: `npm run generate-api` - Regenerate after backend changes
- **Location**: `src/api/generated/` - TypeScript definitions and clients

## Development Workflow

### Local Development
1. **Main Backend**: Spring Boot on port 8080 with H2 database
2. **Auth Backend**: Quarkus on port 8081 with PostgreSQL (or H2 for dev)
3. **Main Frontend**: Vue.js on port 5173 with hot reload
4. **Auth Frontend**: React on port 3001 with hot reload
5. **Database Console**: `http://localhost:8080/h2-console` (dev environment)

### Production Deployment
1. **Backend Services**: Dockerized with Oracle (main) and PostgreSQL (auth)
2. **Frontend Applications**: Optimized builds served via Nginx
3. **Databases**: Oracle Enterprise Edition on port 1521, PostgreSQL on port 5433
4. **Load Balancing**: Nginx reverse proxy for all services

### Testing Strategy
- **Unit Tests**: 
  - JUnit 5 for Spring Boot backend
  - Quarkus Test for authorization service
  - Vitest for Vue.js frontend
  - Vitest for React auth frontend
- **Integration Tests**: 
  - Spring Boot Test with H2 database
  - REST Assured for Quarkus service
- **E2E Tests**: Playwright covering complete user workflows
- **API Testing**: Dedicated script `test-queue-endpoint.sh`

## Key Development Notes

### Message Broker Integration
- Multiple messaging systems supported simultaneously
- Message broker selection via configuration profiles
- Factory pattern enables runtime switching between messaging systems

### Database Configuration
- **Development**: H2 in-memory database for rapid iteration
- **Production**: Oracle with persistent storage and advanced features
- **Migrations**: Handled via `import.sql` files

### Type Safety
- **Backend**: 
  - Strong typing with Java 24 (main service) and Java 17 (auth service)
  - Bean validation annotations
  - OpenAPI 3.0 specification
- **Frontend**: 
  - TypeScript with auto-generated API clients
  - Vue.js 3 with Composition API
  - React 18 with hooks and strict typing
- **API Contract**: OpenAPI specification ensures consistency across services

### Launcher Script Features
- **Environment Detection**: Automatically detects available tools
- **Service Management**: Unified start/stop for all components (auth + main)
- **Logging**: Centralized logs in `/logs/` directory
- **Health Checks**: Validates service startup and readiness
- **Authentication Integration**: Handles auth service dependencies
- **Port Management**: Prevents conflicts between services

### Authentication Flow
1. **User Access**: User visits main application (`http://localhost:5173`)
2. **Auth Check**: Router guard checks for valid JWT token
3. **Redirect**: If unauthenticated, redirects to auth frontend (`http://localhost:3001`)
4. **Login**: User authenticates via React auth interface
5. **Token Exchange**: Auth service returns JWT + refresh token
6. **Callback**: Redirects back to main app with authentication data
7. **Session**: Main app stores tokens and allows access to protected routes