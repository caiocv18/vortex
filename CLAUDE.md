# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Commands

### Development Setup
```bash
# Quick start (recommended)
./start-vortex.sh

# Manual setup
cd backend/vortex-application-service && ./mvnw spring-boot:run
cd frontend/vortex-application-service && npm install && npm run dev
```

### Build Commands
```bash
# Backend (Spring Boot)
cd backend/vortex-application-service
./mvnw clean install          # Build project
./mvnw spring-boot:run        # Run development server
./mvnw spring-boot:build-image # Build Docker image

# Frontend (Vue.js)
cd frontend/vortex-application-service
npm run build                 # Production build
npm run type-check            # TypeScript validation
npm run lint                  # ESLint with auto-fix
npm run generate-api          # Regenerate API client from OpenAPI spec
```

### Testing Commands
```bash
# Backend tests
cd backend/vortex-application-service
./mvnw test                   # All tests
./mvnw test -Dtest=*ControllerTest # Controller tests only

# Frontend tests
cd frontend/vortex-application-service
npm run test:unit             # Vitest unit tests
npm run test:e2e              # Playwright E2E tests
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
./start-vortex.sh --stop          # Stop all services
```

## Architecture Overview

### System Structure
- **Microservices Architecture**: Main application service + authorization service
- **Event-Driven**: Supports Kafka, RabbitMQ, and SQS messaging
- **Multi-Database**: Oracle (production) and H2 (development)
- **Type-Safe**: Auto-generated API clients from OpenAPI specifications

### Backend Architecture (Spring Boot)
- **Pattern**: Layered architecture (Controller → Service → Repository)
- **Key Packages**:
  - `controller/`: REST endpoints
  - `service/`: Business logic and messaging
  - `repository/`: JPA data access
  - `dto/`: Data transfer objects
  - `model/`: JPA entities
  - `config/`: Configuration classes (CORS, messaging, OpenAPI)
  - `factory/`: Message broker factory pattern

### Frontend Architecture (Vue.js)
- **Pattern**: Component-based with centralized state management
- **Key Directories**:
  - `views/`: Page-level components
  - `components/`: Reusable UI components
  - `stores/`: Pinia state management
  - `api/generated/`: Auto-generated API clients
  - `router/`: Vue Router configuration

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

### Base Information
- **Base URL**: `http://localhost:8080/api`
- **Documentation**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `/docs/openapi-backend.json`

### Key Endpoints
- **Products**: `/api/produtos` - Full CRUD operations
- **Product Types**: `/api/tipos-produto` - Category management
- **Movements**: `/api/movimentos` - Inventory transactions
- **Reports**: `/api/relatorios/*` - Business intelligence
- **Health**: `/health` - Application health check

### Frontend API Integration
- **Auto-Generated**: API clients generated from OpenAPI spec
- **Command**: `npm run generate-api` - Regenerate after backend changes
- **Location**: `src/api/generated/` - TypeScript definitions and clients

## Development Workflow

### Local Development
1. **Backend**: Runs on port 8080 with H2 database
2. **Frontend**: Runs on port 5173 with hot reload
3. **Database Console**: `http://localhost:8080/h2-console` (dev environment)

### Production Deployment
1. **Backend**: Dockerized Spring Boot with Oracle database
2. **Frontend**: Optimized build served via Nginx
3. **Database**: Oracle Enterprise Edition on port 1521

### Testing Strategy
- **Unit Tests**: JUnit 5 for backend, Vitest for frontend
- **Integration Tests**: Spring Boot Test with H2 database
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
- **Backend**: Strong typing with Java 24 and validation annotations
- **Frontend**: TypeScript with auto-generated API clients
- **API Contract**: OpenAPI specification ensures consistency

### Launcher Script Features
- **Environment Detection**: Automatically detects available tools
- **Service Management**: Unified start/stop for all components
- **Logging**: Centralized logs in `/logs/` directory
- **Health Checks**: Validates service startup and readiness