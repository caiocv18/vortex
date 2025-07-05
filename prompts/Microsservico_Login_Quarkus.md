### Título: Criação de Microsserviço de Login com Quarkus, OIDC e JWT - Integração com Sistema VORTEX

**Contexto Atual:**

O sistema VORTEX é uma aplicação de controle de estoque construída com Spring Boot que já possui:
- Arquitetura de microsserviços com mensageria (Kafka/RabbitMQ/SQS)
- Sistema de auditoria implementado
- Estrutura de DTOs para eventos
- Configuração de CORS para frontend
- Documentação OpenAPI

Preciso criar um **microsserviço de login independente** usando Quarkus que se integrará com este ecossistema existente. Este serviço será responsável exclusivamente pela autenticação de usuários e emissão de tokens JWT, mantendo consistência com a arquitetura atual.

**Requisitos Essenciais:**

1. **Integração com Arquitetura Existente:**
   - Usar o mesmo padrão de nomenclatura de pacotes: `br.com.vortex.login`
   - Implementar eventos de auditoria compatíveis com o sistema atual
   - Manter consistência com os DTOs existentes (AuditoriaEventDTO, etc.)
   - Configurar CORS para o frontend React com Vite (localhost:3000, 5173, etc.)

2. **Autenticação Múltipla:**
   - **OAuth2/OIDC**: Google e GitHub usando `quarkus-oidc`
   - **Usuário e Senha**: Sistema tradicional com hash bcrypt
   - **Cadastro de Usuário**: Endpoint para criação de contas locais
   - **Recuperação de Senha**: Sistema de reset via email
   - Configurar redirects compatíveis com o frontend React

3. **Persistência de Usuário e Papéis:**
   - Criar entidade `User` com campos: id, email, name, password, roles, createdAt, updatedAt
   - Adicionar campos para recuperação: resetToken, resetTokenExpiry
   - Usar **Quarkus Panache com JPA** para compatibilidade com PostgreSQL
   - Implementar soft delete para usuários
   - Papel padrão: `"user"`, com possibilidade de roles: `["user", "admin", "manager"]`

4. **Geração de Token JWT:**
   - Token JWT com claims obrigatórias: `sub`, `email`, `groups`, `name`, `iat`, `exp`
   - Adicionar claims customizadas: `userId`, `provider` (google/github/local)
   - Configurar expiração compatível com o sistema (ex: 8 horas)

5. **Validação Desacoplada (JWKS):**
   - Endpoint `/auth/jwks.json` para chaves públicas
   - Endpoint `/auth/.well-known/openid-configuration` para descoberta
   - Configurar CORS para permitir acesso do sistema principal

6. **Integração com Sistema de Mensageria:**
   - Publicar eventos de auditoria no mesmo formato do sistema atual
   - Eventos: LOGIN_SUCCESS, LOGIN_FAILED, USER_CREATED, USER_UPDATED, PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED
   - Usar o mesmo padrão de tópicos: `vortex.auditoria`

7. **Sistema de Email:**
   - Integração para envio de emails de recuperação de senha
   - Templates HTML para emails
   - Configuração SMTP

**Estrutura de Implementação:**

**1. Configuração (`application.properties`):**
```properties
# Application
quarkus.application.name=vortex-auth-service
quarkus.http.port=8081

# Database - compatível com sistema atual
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USERNAME:vortex_user}
quarkus.datasource.password=${DB_PASSWORD:vortex_pass}
quarkus.datasource.jdbc.url=${DB_URL:jdbc:postgresql://localhost:5432/vortex_auth}
quarkus.hibernate-orm.database.generation=update
quarkus.hibernate-orm.log.sql=true

# OIDC Providers
quarkus.oidc.google.provider=google
quarkus.oidc.google.client-id=${GOOGLE_CLIENT_ID:placeholder}
quarkus.oidc.google.credentials.secret=${GOOGLE_CLIENT_SECRET:placeholder}
quarkus.oidc.google.authentication.redirect-path=/auth/callback/google

quarkus.oidc.github.provider=github
quarkus.oidc.github.client-id=${GITHUB_CLIENT_ID:placeholder}
quarkus.oidc.github.credentials.secret=${GITHUB_CLIENT_SECRET:placeholder}
quarkus.oidc.github.authentication.redirect-path=/auth/callback/github

# JWT
mp.jwt.verify.publickey.location=META-INF/resources/publickey.pem
mp.jwt.verify.issuer=${JWT_ISSUER:https://auth.vortex.com.br}
smallrye.jwt.sign.key.location=META-INF/resources/privatekey.pem
mp.jwt.verify.audiences=${JWT_AUDIENCE:vortex-system}

# CORS - compatível com frontend React + Vite
quarkus.http.cors=true
quarkus.http.cors.origins=http://localhost:3000,http://localhost:5173,http://localhost:4173,http://127.0.0.1:3000,http://127.0.0.1:5173,http://127.0.0.1:4173
quarkus.http.cors.methods=GET,POST,PUT,DELETE,OPTIONS
quarkus.http.cors.headers=accept,authorization,content-type,x-requested-with
quarkus.http.cors.exposed-headers=authorization

# Email Configuration
quarkus.mailer.from=${MAIL_FROM:noreply@vortex.com.br}
quarkus.mailer.host=${MAIL_HOST:smtp.gmail.com}
quarkus.mailer.port=${MAIL_PORT:587}
quarkus.mailer.start-tls=REQUIRED
quarkus.mailer.username=${MAIL_USERNAME:}
quarkus.mailer.password=${MAIL_PASSWORD:}

# Password Reset
auth.password-reset.token-expiry-minutes=30
auth.password-reset.base-url=${FRONTEND_URL:http://localhost:5173}

# Security
auth.password.min-length=8
auth.password.require-special-chars=true
auth.password.require-numbers=true
auth.password.require-uppercase=true

# Kafka (integração com sistema existente)
kafka.bootstrap.servers=${KAFKA_SERVERS:localhost:9092}
mp.messaging.outgoing.auditoria.connector=smallrye-kafka
mp.messaging.outgoing.auditoria.topic=vortex.auditoria
mp.messaging.outgoing.auditoria.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.auditoria.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer

# Health checks
quarkus.health.extensions.enabled=true
quarkus.health.openapi.included=true

# Metrics
quarkus.micrometer.enabled=true
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.registry-enabled-default=true

# Logging - compatível com sistema atual
quarkus.log.level=INFO
quarkus.log.category."br.com.vortex.login".level=DEBUG
quarkus.log.console.format=%d{HH:mm:ss} %-5p [%c{2.}] (%t) %s%e%n
```

**2. Entidade User (compatível com sistema atual + autenticação local):**
```java
package br.com.vortex.login.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends PanacheEntity {
    
    @Column(unique = true, nullable = false)
    @Email(message = "Email deve ter formato válido")
    @NotBlank(message = "Email é obrigatório")
    public String email;
    
    @Column(nullable = false)
    @NotBlank(message = "Nome é obrigatório")
    public String name;
    
    @Column(name = "password_hash")
    public String passwordHash; // Para login local
    
    @Column(name = "provider")
    @NotNull(message = "Provider é obrigatório")
    public String provider; // google, github, local
    
    @Column(name = "provider_id")
    public String providerId;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    public Set<Role> roles = new HashSet<>();
    
    @Column(name = "created_at")
    public LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
    
    @Column(name = "deleted_at")
    public LocalDateTime deletedAt;
    
    @Column(name = "last_login")
    public LocalDateTime lastLogin;
    
    @Column(name = "login_count")
    public Long loginCount = 0L;
    
    @Column(name = "email_verified")
    public Boolean emailVerified = false;
    
    @Column(name = "reset_token")
    public String resetToken;
    
    @Column(name = "reset_token_expiry")
    public LocalDateTime resetTokenExpiry;
    
    @Column(name = "account_locked")
    public Boolean accountLocked = false;
    
    @Column(name = "failed_login_attempts")
    public Integer failedLoginAttempts = 0;
    
    public enum Role {
        USER, ADMIN, MANAGER
    }
    
    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (roles.isEmpty()) {
            roles.add(Role.USER);
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public static User findByEmail(String email) {
        return find("email = ?1 and deletedAt is null", email).firstResult();
    }
    
    public static User findByProviderAndProviderId(String provider, String providerId) {
        return find("provider = ?1 and providerId = ?2 and deletedAt is null", provider, providerId).firstResult();
    }
    
    public static User findByResetToken(String token) {
        return find("resetToken = ?1 and resetTokenExpiry > ?2 and deletedAt is null", 
                   token, LocalDateTime.now()).firstResult();
    }
    
    public boolean isAccountLocked() {
        return accountLocked != null && accountLocked;
    }
    
    public boolean isResetTokenValid() {
        return resetToken != null && resetTokenExpiry != null && 
               resetTokenExpiry.isAfter(LocalDateTime.now());
    }
}
```

**3. DTOs para Autenticação Local:**
```java
package br.com.vortex.login.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequestDTO {
    @Email(message = "Email deve ter formato válido")
    @NotBlank(message = "Email é obrigatório")
    private String email;
    
    @NotBlank(message = "Senha é obrigatória")
    private String password;
}

@Data
public class RegisterRequestDTO {
    @Email(message = "Email deve ter formato válido")
    @NotBlank(message = "Email é obrigatório")
    private String email;
    
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
    private String name;
    
    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, message = "Senha deve ter pelo menos 8 caracteres")
    private String password;
    
    @NotBlank(message = "Confirmação de senha é obrigatória")
    private String confirmPassword;
}

@Data
public class ForgotPasswordRequestDTO {
    @Email(message = "Email deve ter formato válido")
    @NotBlank(message = "Email é obrigatório")
    private String email;
}

@Data
public class ResetPasswordRequestDTO {
    @NotBlank(message = "Token é obrigatório")
    private String token;
    
    @NotBlank(message = "Nova senha é obrigatória")
    @Size(min = 8, message = "Senha deve ter pelo menos 8 caracteres")
    private String newPassword;
    
    @NotBlank(message = "Confirmação de senha é obrigatória")
    private String confirmPassword;
}

@Data
public class LoginResponseDTO {
    private String token;
    private UserDTO user;
    private String tokenType = "Bearer";
    private Long expiresIn;
}

@Data
public class UserDTO {
    private Long id;
    private String email;
    private String name;
    private String provider;
    private Set<String> roles;
    private LocalDateTime lastLogin;
    private Boolean emailVerified;
}
```

**4. Endpoint de Autenticação Expandido:**
```java
package br.com.vortex.login.resource;

import br.com.vortex.login.service.AuthService;
import br.com.vortex.login.service.AuditService;
import br.com.vortex.login.dto.*;
import br.com.vortex.login.exception.AuthenticationException;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "Endpoints de autenticação")
@Slf4j
public class AuthResource {
    
    @Inject
    AuthService authService;
    
    @Inject
    AuditService auditService;
    
    // ===== AUTENTICAÇÃO LOCAL =====
    
    @POST
    @Path("/login")
    @Operation(summary = "Login com email e senha", description = "Autentica usuário com credenciais locais")
    public Response login(@Valid LoginRequestDTO loginRequest) {
        try {
            log.info("Tentativa de login local para email: {}", loginRequest.getEmail());
            LoginResponseDTO response = authService.authenticateLocal(loginRequest);
            
            auditService.publishLoginEvent(response.getUser(), "LOCAL_LOGIN_SUCCESS", "SUCCESS");
            
            return Response.ok(response).build();
        } catch (AuthenticationException e) {
            log.error("Erro de autenticação local para email: {}", loginRequest.getEmail(), e);
            auditService.publishLoginEvent(null, "LOCAL_LOGIN_FAILED", "FAILED");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Credenciais inválidas", "message", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Erro interno no login local", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro interno do servidor"))
                    .build();
        }
    }
    
    @POST
    @Path("/register")
    @Operation(summary = "Cadastro de novo usuário", description = "Cria nova conta de usuário")
    public Response register(@Valid RegisterRequestDTO registerRequest) {
        try {
            log.info("Tentativa de cadastro para email: {}", registerRequest.getEmail());
            LoginResponseDTO response = authService.registerUser(registerRequest);
            
            auditService.publishUserEvent(response.getUser(), "USER_CREATED", "SUCCESS");
            
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (IllegalArgumentException e) {
            log.error("Erro de validação no cadastro: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Dados inválidos", "message", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Erro interno no cadastro", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro interno do servidor"))
                    .build();
        }
    }
    
    @POST
    @Path("/forgot-password")
    @Operation(summary = "Solicitar recuperação de senha", description = "Envia email para recuperação de senha")
    public Response forgotPassword(@Valid ForgotPasswordRequestDTO forgotPasswordRequest) {
        try {
            log.info("Solicitação de recuperação de senha para email: {}", forgotPasswordRequest.getEmail());
            authService.requestPasswordReset(forgotPasswordRequest.getEmail());
            
            auditService.publishPasswordResetEvent(forgotPasswordRequest.getEmail(), "PASSWORD_RESET_REQUESTED", "SUCCESS");
            
            return Response.ok(Map.of("message", "Se o email existir, você receberá instruções para redefinir sua senha")).build();
        } catch (Exception e) {
            log.error("Erro ao solicitar recuperação de senha", e);
            // Sempre retorna sucesso por segurança (não revelar se email existe)
            return Response.ok(Map.of("message", "Se o email existir, você receberá instruções para redefinir sua senha")).build();
        }
    }
    
    @POST
    @Path("/reset-password")
    @Operation(summary = "Redefinir senha", description = "Redefine senha usando token de recuperação")
    public Response resetPassword(@Valid ResetPasswordRequestDTO resetPasswordRequest) {
        try {
            log.info("Tentativa de redefinição de senha com token");
            authService.resetPassword(resetPasswordRequest);
            
            auditService.publishPasswordResetEvent(null, "PASSWORD_RESET_COMPLETED", "SUCCESS");
            
            return Response.ok(Map.of("message", "Senha redefinida com sucesso")).build();
        } catch (IllegalArgumentException e) {
            log.error("Erro na redefinição de senha: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Token inválido ou expirado", "message", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Erro interno na redefinição de senha", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro interno do servidor"))
                    .build();
        }
    }
    
    // ===== AUTENTICAÇÃO OAUTH =====
    
    @GET
    @Path("/login/{provider}")
    @Operation(summary = "Redireciona para provedor OIDC", description = "Inicia o fluxo de autenticação OAuth2")
    public Response redirectToProvider(@PathParam("provider") String provider) {
        try {
            log.info("Iniciando autenticação para provider: {}", provider);
            String redirectUrl = authService.getAuthorizationUrl(provider);
            return Response.temporaryRedirect(java.net.URI.create(redirectUrl)).build();
        } catch (Exception e) {
            log.error("Erro ao redirecionar para provider: {}", provider, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Provider não suportado: " + provider))
                    .build();
        }
    }
    
    @GET
    @Path("/callback/{provider}")
    @Operation(summary = "Processa callback do provedor", description = "Processa o retorno da autenticação e gera JWT")
    public Response handleCallback(@PathParam("provider") String provider,
                                 @QueryParam("code") String code,
                                 @QueryParam("state") String state) {
        try {
            log.info("Processando callback para provider: {}", provider);
            LoginResponseDTO response = authService.processCallback(provider, code, state);
            
            auditService.publishLoginEvent(response.getUser(), "OAUTH_LOGIN_SUCCESS", "SUCCESS");
            
            return Response.ok(response).build();
        } catch (AuthenticationException e) {
            log.error("Erro de autenticação para provider: {}", provider, e);
            auditService.publishLoginEvent(null, "OAUTH_LOGIN_FAILED", "FAILED");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Falha na autenticação", "message", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Erro interno no callback para provider: {}", provider, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro interno do servidor"))
                    .build();
        }
    }
    
    // ===== ENDPOINTS JWKS =====
    
    @GET
    @Path("/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retorna chaves públicas JWT", description = "Endpoint para validação de tokens JWT")
    public Response getJWKS() {
        try {
            Object jwks = authService.getJWKS();
            return Response.ok(jwks).build();
        } catch (Exception e) {
            log.error("Erro ao obter JWKS", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro ao obter chaves públicas"))
                    .build();
        }
    }
    
    @GET
    @Path("/.well-known/openid-configuration")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Configuração OpenID Connect", description = "Endpoint de descoberta OpenID Connect")
    public Response getOpenIdConfiguration() {
        try {
            Object config = authService.getOpenIdConfiguration();
            return Response.ok(config).build();
        } catch (Exception e) {
            log.error("Erro ao obter configuração OpenID", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro ao obter configuração"))
                    .build();
        }
    }
    
    @GET
    @Path("/health")
    @Operation(summary = "Health check", description = "Verifica saúde do serviço de autenticação")
    public Response healthCheck() {
        return Response.ok(Map.of(
            "status", "UP",
            "service", "vortex-auth-service",
            "timestamp", java.time.LocalDateTime.now(),
            "version", "1.0.0"
        )).build();
    }
}
```

**5. Serviço de Autenticação Local:**
```java
package br.com.vortex.login.service;

import br.com.vortex.login.dto.*;
import br.com.vortex.login.model.User;
import br.com.vortex.login.exception.AuthenticationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.regex.Pattern;

@ApplicationScoped
@Slf4j
public class LocalAuthService {
    
    @Inject
    PasswordService passwordService;
    
    @Inject
    JwtService jwtService;
    
    @Inject
    EmailService emailService;
    
    @ConfigProperty(name = "auth.password.min-length", defaultValue = "8")
    int minPasswordLength;
    
    @ConfigProperty(name = "auth.password-reset.token-expiry-minutes", defaultValue = "30")
    int resetTokenExpiryMinutes;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$");
    
    @Transactional
    public LoginResponseDTO authenticateLocal(LoginRequestDTO loginRequest) {
        User user = User.findByEmail(loginRequest.getEmail());
        
        if (user == null || !user.provider.equals("local")) {
            throw new AuthenticationException("Credenciais inválidas");
        }
        
        if (user.isAccountLocked()) {
            throw new AuthenticationException("Conta bloqueada. Entre em contato com o suporte.");
        }
        
        if (!passwordService.verifyPassword(loginRequest.getPassword(), user.passwordHash)) {
            user.failedLoginAttempts++;
            if (user.failedLoginAttempts >= 5) {
                user.accountLocked = true;
                log.warn("Conta bloqueada por tentativas excessivas: {}", user.email);
            }
            user.persist();
            throw new AuthenticationException("Credenciais inválidas");
        }
        
        // Reset failed attempts on successful login
        user.failedLoginAttempts = 0;
        user.lastLogin = LocalDateTime.now();
        user.loginCount++;
        user.persist();
        
        String token = jwtService.generateToken(user);
        
        return createLoginResponse(user, token);
    }
    
    @Transactional
    public LoginResponseDTO registerUser(RegisterRequestDTO registerRequest) {
        validateRegistration(registerRequest);
        
        if (User.findByEmail(registerRequest.getEmail()) != null) {
            throw new IllegalArgumentException("Email já está em uso");
        }
        
        User user = new User();
        user.email = registerRequest.getEmail();
        user.name = registerRequest.getName();
        user.provider = "local";
        user.passwordHash = passwordService.hashPassword(registerRequest.getPassword());
        user.emailVerified = false; // Pode implementar verificação por email
        user.persist();
        
        String token = jwtService.generateToken(user);
        
        return createLoginResponse(user, token);
    }
    
    @Transactional
    public void requestPasswordReset(String email) {
        User user = User.findByEmail(email);
        
        if (user == null || !user.provider.equals("local")) {
            log.info("Tentativa de reset para email não encontrado ou não local: {}", email);
            return; // Não revelar se email existe
        }
        
        String resetToken = generateResetToken();
        user.resetToken = resetToken;
        user.resetTokenExpiry = LocalDateTime.now().plusMinutes(resetTokenExpiryMinutes);
        user.persist();
        
        emailService.sendPasswordResetEmail(user.email, user.name, resetToken);
        log.info("Email de recuperação enviado para: {}", email);
    }
    
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO resetRequest) {
        if (!resetRequest.getNewPassword().equals(resetRequest.getConfirmPassword())) {
            throw new IllegalArgumentException("Senhas não coincidem");
        }
        
        validatePassword(resetRequest.getNewPassword());
        
        User user = User.findByResetToken(resetRequest.getToken());
        
        if (user == null) {
            throw new IllegalArgumentException("Token inválido ou expirado");
        }
        
        user.passwordHash = passwordService.hashPassword(resetRequest.getNewPassword());
        user.resetToken = null;
        user.resetTokenExpiry = null;
        user.failedLoginAttempts = 0;
        user.accountLocked = false;
        user.persist();
        
        log.info("Senha redefinida com sucesso para usuário: {}", user.email);
    }
    
    private void validateRegistration(RegisterRequestDTO registerRequest) {
        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
            throw new IllegalArgumentException("Senhas não coincidem");
        }
        
        if (!EMAIL_PATTERN.matcher(registerRequest.getEmail()).matches()) {
            throw new IllegalArgumentException("Email inválido");
        }
        
        validatePassword(registerRequest.getPassword());
    }
    
    private void validatePassword(String password) {
        if (password.length() < minPasswordLength) {
            throw new IllegalArgumentException("Senha deve ter pelo menos " + minPasswordLength + " caracteres");
        }
        
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("Senha deve conter pelo menos uma letra minúscula, uma maiúscula, um número e um caractere especial");
        }
    }
    
    private String generateResetToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    private LoginResponseDTO createLoginResponse(User user, String token) {
        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setUser(convertToUserDTO(user));
        response.setExpiresIn(8 * 60 * 60L); // 8 horas em segundos
        return response;
    }
    
    private UserDTO convertToUserDTO(User user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.id);
        userDTO.setEmail(user.email);
        userDTO.setName(user.name);
        userDTO.setProvider(user.provider);
        userDTO.setRoles(user.roles.stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        userDTO.setLastLogin(user.lastLogin);
        userDTO.setEmailVerified(user.emailVerified);
        return userDTO;
    }
}
```

**6. Serviço de Email:**
```java
package br.com.vortex.login.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
public class EmailService {
    
    @Inject
    Mailer mailer;
    
    @ConfigProperty(name = "auth.password-reset.base-url")
    String baseUrl;
    
    public void sendPasswordResetEmail(String email, String name, String resetToken) {
        try {
            String resetLink = baseUrl + "/reset-password?token=" + resetToken;
            
            String htmlContent = buildPasswordResetEmailTemplate(name, resetLink);
            
            mailer.send(Mail.withHtml(email, "Redefinição de Senha - VORTEX", htmlContent));
            
            log.info("Email de recuperação enviado para: {}", email);
        } catch (Exception e) {
            log.error("Erro ao enviar email de recuperação para: {}", email, e);
            throw new RuntimeException("Erro ao enviar email");
        }
    }
    
    private String buildPasswordResetEmailTemplate(String name, String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Redefinição de Senha</title>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #1976d2; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .button { display: inline-block; padding: 12px 24px; background-color: #1976d2; 
                             color: white; text-decoration: none; border-radius: 4px; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🔐 VORTEX - Redefinição de Senha</h1>
                    </div>
                    <div class="content">
                        <h2>Olá, %s!</h2>
                        <p>Você solicitou a redefinição de sua senha no sistema VORTEX.</p>
                        <p>Clique no botão abaixo para redefinir sua senha:</p>
                        <a href="%s" class="button">Redefinir Senha</a>
                        <p>Se você não solicitou esta redefinição, ignore este email. Sua senha permanecerá inalterada.</p>
                        <p><strong>Este link expira em 30 minutos.</strong></p>
                    </div>
                    <div class="footer">
                        <p>Este é um email automático. Não responda a este email.</p>
                        <p>© 2024 VORTEX - Sistema de Controle de Estoque</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(name, resetLink);
    }
}
```

**7. Serviço de Auditoria Expandido:**
```java
package br.com.vortex.login.service;

import br.com.vortex.login.dto.AuditoriaEventDTO;
import br.com.vortex.login.dto.UserDTO;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
@Slf4j
public class AuditService {
    
    @Channel("auditoria")
    Emitter<AuditoriaEventDTO> auditoriaEmitter;
    
    public void publishLoginEvent(UserDTO user, String action, String status) {
        try {
            AuditoriaEventDTO event = new AuditoriaEventDTO();
            event.setAcao(action);
            event.setEntidade("USER");
            event.setEntidadeId(user != null ? user.getId() : null);
            event.setUserId(user != null ? user.getEmail() : "unknown");
            event.setResultado(status);
            event.setDetalhes(user != null ? "Login via " + user.getProvider() : "Login failed");
            
            auditoriaEmitter.send(event);
            log.debug("Evento de auditoria publicado: {}", action);
        } catch (Exception e) {
            log.error("Erro ao publicar evento de auditoria", e);
        }
    }
    
    public void publishUserEvent(UserDTO user, String action, String status) {
        try {
            AuditoriaEventDTO event = new AuditoriaEventDTO();
            event.setAcao(action);
            event.setEntidade("USER");
            event.setEntidadeId(user.getId());
            event.setUserId(user.getEmail());
            event.setResultado(status);
            event.setDetalhes("User " + action.toLowerCase() + " - Provider: " + user.getProvider());
            
            auditoriaEmitter.send(event);
            log.debug("Evento de usuário publicado: {}", action);
        } catch (Exception e) {
            log.error("Erro ao publicar evento de usuário", e);
        }
    }
    
    public void publishPasswordResetEvent(String email, String action, String status) {
        try {
            AuditoriaEventDTO event = new AuditoriaEventDTO();
            event.setAcao(action);
            event.setEntidade("PASSWORD_RESET");
            event.setUserId(email != null ? email : "unknown");
            event.setResultado(status);
            event.setDetalhes("Password reset operation");
            
            auditoriaEmitter.send(event);
            log.debug("Evento de reset de senha publicado: {}", action);
        } catch (Exception e) {
            log.error("Erro ao publicar evento de reset de senha", e);
        }
    }
}
```

**Fluxo de Integração Expandido:**

1. **Frontend React** apresenta tela de login com opções:
   - Login com email/senha
   - Botão "Cadastrar-se"
   - Botão "Esqueci minha senha"
   - Botões para Google/GitHub

2. **Autenticação Local:**
   - POST `/auth/login` com email/senha
   - POST `/auth/register` para cadastro
   - POST `/auth/forgot-password` para recuperação
   - POST `/auth/reset-password` com token

3. **Autenticação OAuth:** (fluxo existente)
   - GET `/auth/login/{provider}` para redirecionamento
   - GET `/auth/callback/{provider}` para callback

4. **Todos os fluxos** geram JWT e publicam eventos de auditoria

**Frontend - Padrão de Cores e Estilos:**

As telas de login devem seguir o mesmo padrão visual da aplicação principal VORTEX:

**Paleta de Cores (compatível com a aplicação principal):**
```css
/* Cores principais da aplicação VORTEX */
:root {
  --vt-c-white: #ffffff;
  --vt-c-white-soft: #f8f8f8;
  --vt-c-white-mute: #f2f2f2;
  --vt-c-black: #181818;
  --vt-c-black-soft: #222222;
  --vt-c-black-mute: #282828;
  --vt-c-indigo: #2c3e50;
  
  /* Cor de destaque verde */
  --vt-c-green: hsla(160, 100%, 37%, 1);
  --vt-c-green-hover: hsla(160, 100%, 37%, 0.2);
  
  /* Cores semânticas */
  --color-background: var(--vt-c-white);
  --color-background-soft: var(--vt-c-white-soft);
  --color-background-mute: var(--vt-c-white-mute);
  --color-heading: var(--vt-c-text-light-1);
  --color-text: var(--vt-c-text-light-1);
  --color-border: rgba(60, 60, 60, 0.12);
  --color-border-hover: rgba(60, 60, 60, 0.29);
}

/* Suporte a dark mode */
@media (prefers-color-scheme: dark) {
  :root {
    --color-background: var(--vt-c-black);
    --color-background-soft: var(--vt-c-black-soft);
    --color-background-mute: var(--vt-c-black-mute);
    --color-heading: var(--vt-c-white);
    --color-text: rgba(235, 235, 235, 0.64);
    --color-border: rgba(84, 84, 84, 0.48);
    --color-border-hover: rgba(84, 84, 84, 0.65);
  }
}
```

**Exemplo de Tela React (seguindo padrão visual VORTEX):**
```jsx
// LoginPage.jsx
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './LoginPage.css'; // Estilos compatíveis com VORTEX

const LoginPage = () => {
  const [isLogin, setIsLogin] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showForgotPassword, setShowForgotPassword] = useState(false);
  
  const navigate = useNavigate();
  
  const handleSubmit = async (e) => {
    e.preventDefault();
    // Implementar chamadas para API
  };
  
  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <h1 className="vortex-title">🏢 VORTEX</h1>
          <p className="vortex-subtitle">Sistema de Controle de Estoque</p>
        </div>
        
        {!showForgotPassword ? (
          <>
            <form onSubmit={handleSubmit} className="login-form">
              <div className="form-group">
                <input
                  type="email"
                  placeholder="Email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="form-input"
                  required
                />
              </div>
              
              {!isLogin && (
                <div className="form-group">
                  <input
                    type="text"
                    placeholder="Nome completo"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    className="form-input"
                    required
                  />
                </div>
              )}
              
              <div className="form-group">
                <input
                  type="password"
                  placeholder="Senha"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="form-input"
                  required
                />
              </div>
              
              {!isLogin && (
                <div className="form-group">
                  <input
                    type="password"
                    placeholder="Confirmar senha"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    className="form-input"
                    required
                  />
                </div>
              )}
              
              <button type="submit" className="btn-primary">
                {isLogin ? 'Entrar' : 'Cadastrar'}
              </button>
            </form>
            
            <div className="auth-options">
              <button 
                type="button" 
                onClick={() => setIsLogin(!isLogin)}
                className="btn-link"
              >
                {isLogin ? 'Criar conta' : 'Já tenho conta'}
              </button>
              
              {isLogin && (
                <button 
                  type="button" 
                  onClick={() => setShowForgotPassword(true)}
                  className="btn-link"
                >
                  Esqueci minha senha
                </button>
              )}
            </div>
            
            <div className="divider">
              <span>ou</span>
            </div>
            
            <div className="oauth-buttons">
              <button 
                onClick={() => window.location.href = '/auth/login/google'}
                className="btn-oauth btn-google"
              >
                <span className="oauth-icon">🔍</span>
                Entrar com Google
              </button>
              <button 
                onClick={() => window.location.href = '/auth/login/github'}
                className="btn-oauth btn-github"
              >
                <span className="oauth-icon">🐙</span>
                Entrar com GitHub
              </button>
            </div>
          </>
        ) : (
          <ForgotPasswordForm onBack={() => setShowForgotPassword(false)} />
        )}
      </div>
    </div>
  );
};

export default LoginPage;
```

**Arquivo CSS para Login (LoginPage.css):**
```css
/* LoginPage.css - Seguindo padrão VORTEX */
.login-container {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, var(--color-background) 0%, var(--color-background-soft) 100%);
  font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.login-card {
  background: var(--color-background);
  border-radius: 12px;
  padding: 2rem;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--color-border);
  width: 100%;
  max-width: 400px;
  margin: 1rem;
}

.login-header {
  text-align: center;
  margin-bottom: 2rem;
}

.vortex-title {
  color: var(--color-heading);
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 0.5rem;
}

.vortex-subtitle {
  color: var(--color-text);
  font-size: 0.9rem;
  opacity: 0.8;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-input {
  padding: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  font-size: 1rem;
  background: var(--color-background);
  color: var(--color-text);
  transition: border-color 0.3s ease;
}

.form-input:focus {
  outline: none;
  border-color: var(--vt-c-green);
  box-shadow: 0 0 0 2px var(--vt-c-green-hover);
}

.btn-primary {
  padding: 0.75rem 1.5rem;
  background: var(--vt-c-green);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-primary:hover {
  background: var(--vt-c-green);
  opacity: 0.9;
  transform: translateY(-1px);
}

.auth-options {
  display: flex;
  justify-content: space-between;
  margin: 1.5rem 0;
  gap: 1rem;
}

.btn-link {
  background: none;
  border: none;
  color: var(--vt-c-green);
  cursor: pointer;
  text-decoration: none;
  font-size: 0.9rem;
  padding: 0.25rem;
  transition: background-color 0.3s ease;
}

.btn-link:hover {
  background-color: var(--vt-c-green-hover);
  border-radius: 4px;
}

.divider {
  text-align: center;
  margin: 1.5rem 0;
  position: relative;
  color: var(--color-text);
  opacity: 0.6;
}

.divider::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--color-border);
}

.divider span {
  background: var(--color-background);
  padding: 0 1rem;
}

.oauth-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.btn-oauth {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-background);
  color: var(--color-text);
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-oauth:hover {
  border-color: var(--color-border-hover);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.oauth-icon {
  font-size: 1.2rem;
}

/* Responsividade */
@media (max-width: 480px) {
  .login-card {
    margin: 0.5rem;
    padding: 1.5rem;
  }
  
  .auth-options {
    flex-direction: column;
    text-align: center;
  }
}

/* Dark mode support */
@media (prefers-color-scheme: dark) {
  .login-card {
    box-shadow: 0 8px 32px rgba(255, 255, 255, 0.1);
  }
  
  .form-input {
    background: var(--color-background-mute);
  }
  
  .divider span {
    background: var(--color-background);
  }
}
```

**Dependências do pom.xml Atualizadas:**
```xml
<dependencies>
    <!-- Autenticação -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-oidc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-smallrye-jwt</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-security-jpa</artifactId>
    </dependency>
    
    <!-- Banco de dados -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-hibernate-orm-panache</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-postgresql</artifactId>
    </dependency>
    
    <!-- Email -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-mailer</artifactId>
    </dependency>
    
    <!-- Mensageria -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>
    </dependency>
    
    <!-- Monitoramento -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-smallrye-health</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
    </dependency>
    
    <!-- Documentação -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-smallrye-openapi</artifactId>
    </dependency>
    
    <!-- Validação -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-hibernate-validator</artifactId>
    </dependency>
    
    <!-- Criptografia -->
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-crypto</artifactId>
        <version>6.2.0</version>
    </dependency>
</dependencies>
```

Este prompt expandido agora inclui:

✅ **Autenticação Local** com usuário e senha
✅ **Cadastro de usuários** com validação completa
✅ **Recuperação de senha** via email
✅ **Templates de email** profissionais
✅ **Segurança avançada** (bloqueio de conta, validação de senha)
✅ **Compatibilidade com React + Vite**
✅ **Auditoria completa** de todas as operações
✅ **Integração perfeita** com o sistema VORTEX existente