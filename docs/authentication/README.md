# Vortex Authentication Service

A complete authentication and authorization service for the Vortex inventory management system.

## Architecture

The authentication service consists of two main components:

### Backend (Quarkus) - Port 8081
- **Location**: `backend/vortex-authorization-service/`
- **Technology**: Quarkus (Java 17)
- **Database**: PostgreSQL 17 with `auth` schema
- **Authentication**: JWT (JSON Web Tokens) with refresh tokens
- **API Documentation**: Available at `http://localhost:8081/q/swagger-ui`

### Frontend (React) - Port 3001  
- **Location**: `frontend/vortex-authorization-service/`
- **Technology**: React 18 with TypeScript, Vite, TailwindCSS
- **Pages**: Login, Registration, Password Recovery
- **State Management**: React Context API

## Features

### Authentication Features
- ✅ User registration with email verification
- ✅ Login with email/username and password
- ✅ JWT access tokens (15 minutes) and refresh tokens (7 days)
- ✅ Password recovery via email
- ✅ Rate limiting (5 attempts per 15 minutes)
- ✅ Account lockout protection
- ✅ Password policy enforcement

### Security Features
- ✅ BCrypt password hashing
- ✅ JWT token blacklisting
- ✅ Audit logging for all authentication events
- ✅ IP-based rate limiting
- ✅ CORS configuration
- ✅ Security headers

### User Management
- ✅ Role-based access control (ADMIN, USER, MANAGER, VIEWER)
- ✅ User profile management
- ✅ Account activation/deactivation
- ✅ Session management

## Database Schema

The service uses a dedicated `auth` schema in PostgreSQL with the following tables:

- `auth.users` - User accounts
- `auth.credentials` - Password hashes
- `auth.roles` - User roles
- `auth.user_roles` - User-role associations
- `auth.refresh_tokens` - Active refresh tokens
- `auth.password_reset_tokens` - Password reset tokens
- `auth.token_blacklist` - Revoked tokens
- `auth.audit_logs` - Authentication events
- `auth.login_attempts` - Rate limiting data

## API Endpoints

### Authentication Endpoints
- `POST /api/auth/login` - User login
- `POST /api/auth/register` - User registration  
- `POST /api/auth/logout` - User logout
- `POST /api/auth/refresh` - Refresh access token
- `POST /api/auth/forgot-password` - Request password reset
- `POST /api/auth/reset-password` - Reset password with token

### Health & Monitoring
- `GET /q/health` - Service health check
- `GET /q/swagger-ui` - API documentation

## Test Users

The system includes pre-configured test users:

| Email | Username | Password | Role(s) | Status |
|-------|----------|----------|---------|---------|
| admin@vortex.com | admin | Test@123 | ADMIN, USER | Active |
| user@vortex.com | user | Test@123 | USER | Active |
| manager@vortex.com | manager | Test@123 | MANAGER, USER | Active |
| viewer@vortex.com | viewer | Test@123 | VIEWER | Active |

## Getting Started

### Development Setup

1. **Start the authentication services:**
```bash
# From project root
./start-vortex.sh -e dev -c backend -m kafka

# Or manually:
cd backend/vortex-authorization-service
mvn quarkus:dev
```

2. **Start the frontend:**
```bash
cd frontend/vortex-authorization-service
npm install
npm run dev
```

3. **Access the services:**
- Auth Frontend: http://localhost:3001
- Auth Backend API: http://localhost:8081
- API Documentation: http://localhost:8081/q/swagger-ui
- Main Application: http://localhost:5173

### Docker Setup

```bash
# Start auth services with Docker
docker-compose -f infra/docker/docker-compose.auth.yml up -d

# Or as part of full stack
./start-vortex.sh -e prd -m kafka
```

## Integration with Main Application

The main Vue.js application (port 5173) integrates with the auth service through:

1. **Router Guards**: Check for valid tokens before accessing protected routes
2. **Auth Store**: Manages authentication state and token refresh
3. **Auto-redirect**: Unauthenticated users are redirected to login (port 3001)
4. **Token Management**: Automatic token refresh and secure storage

### Authentication Flow

1. User accesses main application (http://localhost:5173)
2. Router guard checks for valid authentication
3. If not authenticated, redirects to auth service (http://localhost:3001)
4. User logs in via React auth frontend
5. Auth service redirects back to main app with tokens
6. Main app stores tokens and continues normal operation

## Configuration

### Environment Variables

#### Backend Configuration
```properties
# Database
DB_URL=jdbc:postgresql://localhost:5433/vortex_auth
DB_USER=vortex_auth
DB_PASSWORD=vortex_auth_password

# JWT
JWT_SECRET=your-secret-key-here
AUTH_JWT_ACCESS_TOKEN_EXPIRATION=15m
AUTH_JWT_REFRESH_TOKEN_EXPIRATION=7d

# Rate Limiting
AUTH_RATE_LIMIT_LOGIN_ATTEMPTS=5
AUTH_RATE_LIMIT_WINDOW_MINUTES=15
```

#### Frontend Configuration
```env
VITE_API_URL=http://localhost:8081
```

## Security Considerations

### Password Policy
- Minimum 8 characters
- At least one uppercase letter
- At least one lowercase letter  
- At least one number
- At least one special character

### Token Security
- Access tokens expire in 15 minutes
- Refresh tokens expire in 7 days
- Tokens are automatically revoked on logout
- JWT tokens include user roles and permissions

### Rate Limiting
- Maximum 5 login attempts per 15-minute window
- Lockout period of 30 minutes after exceeding limit
- IP-based and email-based tracking

## Monitoring & Logging

### Health Checks
- Backend health: `GET /q/health`
- Database connectivity check
- Service dependency validation

### Audit Logging
All authentication events are logged with:
- User ID and email
- Action performed (login, logout, password change, etc.)
- IP address and user agent
- Timestamp and additional context

### Metrics
- Login success/failure rates
- Active user sessions
- Password reset requests
- Rate limiting violations

## Development

### Adding New Features

1. **Backend Changes**: Add to `backend/vortex-authorization-service/`
2. **Frontend Changes**: Add to `frontend/vortex-authorization-service/`
3. **Database Changes**: Create migration in `src/main/resources/db/migration/`
4. **API Changes**: Update OpenAPI documentation

### Testing

```bash
# Backend tests
cd backend/vortex-authorization-service
mvn test

# Frontend tests  
cd frontend/vortex-authorization-service
npm test
```

## Troubleshooting

### Common Issues

1. **Token Expired**: Clear localStorage and login again
2. **CORS Errors**: Check backend CORS configuration
3. **Database Connection**: Verify PostgreSQL is running on port 5433
4. **Port Conflicts**: Use `./scripts/check-ports.sh` to verify port availability

### Logs Location
- Development: Console output
- Docker: `docker logs vortex-auth-service`
- Production: `/opt/app/logs/`

## Contributing

1. Follow existing code structure and patterns
2. Add tests for new features
3. Update documentation
4. Ensure security best practices
5. Test integration with main application