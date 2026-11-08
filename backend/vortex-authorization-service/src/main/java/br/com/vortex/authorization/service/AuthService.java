package br.com.vortex.authorization.service;

import br.com.vortex.authorization.dto.*;
import br.com.vortex.authorization.entity.*;
import br.com.vortex.authorization.event.*;
import br.com.vortex.authorization.security.JwtService;
import br.com.vortex.authorization.security.PasswordService;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonString;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    @Inject
    JwtService jwtService;

    @Inject
    JWTParser jwtParser;

    @Inject
    PasswordService passwordService;

    @Inject
    RateLimitService rateLimitService;

    @Inject
    EventPublisher eventPublisher;

    @Inject
    EmailService emailService;

    @Inject
    EntityManager entityManager;

    /**
     * Login com senha. A falha (401) e o bloqueio por rate limit (400, que o resource devolve como 401)
     * saem como exceção, e mesmo assim a transação faz commit (dontRollbackOn): a falha gravada pelo
     * RateLimitService entra no mesmo commit, na mesma conexão do pool que a contagem já usou.
     *
     * Antes a falha ia numa transação REQUIRES_NEW, que pedia uma segunda conexão com a primeira presa
     * pela transação suspensa do login. Com logins errados simultâneos no tamanho do pool (5 no modo
     * leve) ninguém conseguia a segunda, tudo esperava o acquisition-timeout e a falha não era gravada.
     * Nenhum dos dois caminhos de exceção grava outra coisa além da falha, então o commit é seguro.
     */
    @Transactional(dontRollbackOn = {NotAuthorizedException.class, BadRequestException.class})
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        // Tentativa barrada pelo limite não conta como falha nova: o bloqueio acaba sozinho quando as
        // falhas que o causaram saem da janela (auth.rate-limit.window-minutes). Gravada, cada tentativa
        // barrada empurrava o desbloqueio, e uma requisição a cada 3 minutos travava a conta de vez.
        if (!rateLimitService.isAllowed(request.identifier, ipAddress)) {
            throw new BadRequestException("Too many failed login attempts. Please try again later.");
        }

        // Find user
        User user = User.findByEmailOrUsername(request.identifier);
        if (user == null || !user.isActiveAndVerified()) {
            rateLimitService.recordFailedAttempt(request.identifier, ipAddress);
            throw unauthorized("Invalid credentials");
        }

        // Verify password
        Credential credential = Credential.findByUserId(user.id);
        if (credential == null || !passwordService.verifyPassword(request.password, credential.passwordHash)) {
            rateLimitService.recordFailedAttempt(request.identifier, ipAddress);
            throw unauthorized("Invalid credentials");
        }

        // Record successful login
        LoginAttempt.recordAttempt(request.identifier, ipAddress, true);
        
        // Update last login
        user.lastLogin = OffsetDateTime.now();
        user.persist();

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = jwtService.generateRefreshToken(user);

        // Store refresh token
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.user = user;
        refreshToken.token = refreshTokenValue;
        refreshToken.expiresAt = OffsetDateTime.now().plus(jwtService.getRefreshTokenExpiration());
        refreshToken.persist();

        // Create response
        LoginResponse response = new LoginResponse();
        response.accessToken = accessToken;
        response.refreshToken = refreshTokenValue;
        response.expiresIn = jwtService.getAccessTokenExpirationSeconds();
        response.user = mapToUserResponse(user);

        // Log audit event
        AuditLog.log(user, "LOGIN_SUCCESS", 
            Map.of("method", "password", "ipAddress", ipAddress), 
            ipAddress, userAgent);

        // Publish login event (o EventPublisher só envia depois do commit). O sessionId é o id da linha
        // do refresh token: identifica a sessão sem autenticar nada. O próprio token é credencial de 7
        // dias, e no evento ia para o tópico do broker e para o log de quem lesse o payload.
        try {
            UserLoggedInEvent loginEvent = new UserLoggedInEvent(
                user.id,
                user.email,
                user.username,
                roleNames(user),
                "password",
                user.lastLogin,
                sessionId(refreshToken),
                ipAddress,
                userAgent
            );
            eventPublisher.publishEvent(loginEvent);
        } catch (Exception e) {
            // Don't fail login if event publishing fails
            LOGGER.warn("Failed to publish login event for user: {}", user.email, e);
        }

        return response;
    }

    @Transactional
    public LoginResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        // Validate passwords match
        if (!request.password.equals(request.confirmPassword)) {
            throw new BadRequestException("Passwords do not match");
        }

        // Validate password policy
        if (!passwordService.isValidPassword(request.password)) {
            throw new BadRequestException("Password does not meet requirements: " + 
                passwordService.getPasswordRequirements());
        }

        // Check if user already exists
        if (User.findByEmail(request.email) != null) {
            throw new BadRequestException("Email already registered");
        }

        if (User.findByUsername(request.username) != null) {
            throw new BadRequestException("Username already taken");
        }

        // Create user
        User user = new User();
        user.email = request.email;
        user.username = request.username;
        user.isActive = true;
        user.isVerified = false; // Require email verification
        user.persist();

        // Create credential
        Credential credential = new Credential();
        credential.user = user;
        credential.passwordHash = passwordService.hashPassword(request.password);
        credential.persist();

        // Assign default USER role
        Role userRole = Role.findUserRole();
        if (userRole != null) {
            user.roles = Set.of(userRole);
        } else {
            // Initialize empty roles set if no default role found
            user.roles = Set.of();
        }
        
        // Persist user with roles
        user.persist();

        // Log audit event
        AuditLog.log(user, "USER_CREATED",
            Map.of("method", "registration", "ipAddress", ipAddress),
            ipAddress, userAgent);

        // For now, auto-verify user (in production, send verification email)
        user.isVerified = true;
        user.persist();

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = jwtService.generateRefreshToken(user);

        // Store refresh token
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.user = user;
        refreshToken.token = refreshTokenValue;
        refreshToken.expiresAt = OffsetDateTime.now().plusDays(7);
        refreshToken.persist();

        // Create response
        LoginResponse response = new LoginResponse();
        response.accessToken = accessToken;
        response.refreshToken = refreshTokenValue;
        response.expiresIn = jwtService.getAccessTokenExpirationSeconds();
        response.user = mapToUserResponse(user);

        // Publish user created event: montado depois da verificação automática, com o estado final do
        // usuário (antes saía com isVerified=false). O EventPublisher só envia depois do commit, então
        // um cadastro que falha no commit (e-mail duplicado em corrida) não anuncia usuário nenhum.
        try {
            UserCreatedEvent userCreatedEvent = new UserCreatedEvent(
                user.id,
                user.email,
                user.username,
                roleNames(user),
                user.isVerified,
                "registration",
                ipAddress,
                userAgent
            );
            eventPublisher.publishEvent(userCreatedEvent);
        } catch (Exception e) {
            LOGGER.warn("Failed to publish user created event for user: {}", user.email, e);
        }

        return response;
    }

    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = RefreshToken.findValidToken(request.refreshToken);
        if (refreshToken == null) {
            throw unauthorized("Invalid or expired refresh token");
        }

        User user = refreshToken.user;
        if (!user.isActiveAndVerified()) {
            throw unauthorized("User account is not active");
        }

        // Generate new access token
        String accessToken = jwtService.generateAccessToken(user);

        // Create response
        LoginResponse response = new LoginResponse();
        response.accessToken = accessToken;
        response.refreshToken = request.refreshToken; // Keep same refresh token
        response.expiresIn = jwtService.getAccessTokenExpirationSeconds();
        response.user = mapToUserResponse(user);

        return response;
    }

    @Transactional
    public void logout(String refreshTokenValue, String ipAddress, String userAgent) {
        RefreshToken refreshToken = RefreshToken.findByToken(refreshTokenValue);
        if (refreshToken != null) {
            User user = refreshToken.user;
            refreshToken.revoke();
            
            // Log audit event
            AuditLog.log(user, "LOGOUT", 
                Map.of("ipAddress", ipAddress), 
                ipAddress, userAgent);

            // Publish logout event
            try {
                UserLoggedOutEvent logoutEvent = new UserLoggedOutEvent(
                    user.id,
                    user.email,
                    user.username,
                    sessionId(refreshToken),
                    "user_initiated",
                    ipAddress,
                    userAgent
                );
                eventPublisher.publishEvent(logoutEvent);
            } catch (Exception e) {
                LOGGER.warn("Failed to publish logout event for user: {}", user.email, e);
            }
        }
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, String ipAddress, String userAgent) {
        // Check rate limiting first
        if (!rateLimitService.isAllowed(request.email, ipAddress)) {
            throw new BadRequestException("Too many failed login attempts. Please try again later.");
        }

        User user = User.findByEmail(request.email);
        if (user == null || !user.isActive) {
            // Don't reveal if email exists
            return;
        }

        // Expire existing tokens
        PasswordResetToken.expireAllUserTokens(user.id);

        // Create new reset token
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.user = user;
        resetToken.token = passwordService.generateSecureToken(32);
        resetToken.expiresAt = OffsetDateTime.now().plusHours(1);
        resetToken.persist();

        // Log audit event
        AuditLog.log(user, "PASSWORD_RESET_REQUESTED", 
            Map.of("ipAddress", ipAddress), 
            ipAddress, userAgent);

        // Publish password reset requested event
        try {
            PasswordResetRequestedEvent resetRequestedEvent = new PasswordResetRequestedEvent(
                user.id,
                user.email,
                user.username,
                resetToken.id,
                resetToken.expiresAt,
                "email",
                ipAddress,
                userAgent
            );
            eventPublisher.publishEvent(resetRequestedEvent);
        } catch (Exception e) {
            LOGGER.warn("Failed to publish password reset requested event for user: {}", user.email, e);
        }

        // Send password reset email asynchronously
        emailService.sendPasswordResetEmail(user.email, resetToken.token)
                .subscribe()
                .with(
                    unused -> LOGGER.info("Password reset email sent successfully to: {}", user.email),
                    throwable -> LOGGER.error("Failed to send password reset email to: {}", user.email, throwable)
                );
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ipAddress, String userAgent) {
        // Validate passwords match
        if (!request.password.equals(request.confirmPassword)) {
            throw new BadRequestException("Passwords do not match");
        }

        // Validate password policy
        if (!passwordService.isValidPassword(request.password)) {
            throw new BadRequestException("Password does not meet requirements: " + 
                passwordService.getPasswordRequirements());
        }

        // Find valid token
        PasswordResetToken resetToken = PasswordResetToken.findValidToken(request.token);
        if (resetToken == null) {
            throw new BadRequestException("Invalid or expired reset token");
        }

        User user = resetToken.user;
        if (!user.isActive) {
            throw new BadRequestException("User account is not active");
        }

        // Consome o token numa única instrução condicional, antes de trocar a senha: com duas
        // requisições concorrentes usando o mesmo token, só uma consegue marcá-lo como usado.
        if (!PasswordResetToken.consume(resetToken.id)) {
            throw new BadRequestException("Invalid or expired reset token");
        }

        // Update password
        Credential credential = Credential.findByUserId(user.id);
        credential.passwordHash = passwordService.hashPassword(request.password);
        credential.persist();

        // Mark token as used and invalidate all other reset tokens
        resetToken.markAsUsed();
        PasswordResetToken.expireAllUserTokens(user.id);

        // Revoke all refresh tokens
        RefreshToken.revokeAllUserTokens(user.id);

        // Log audit event
        AuditLog.log(user, "PASSWORD_RESET", 
            Map.of("ipAddress", ipAddress), 
            ipAddress, userAgent);

        // Publish password changed event
        try {
            PasswordChangedEvent passwordChangedEvent = new PasswordChangedEvent(
                user.id,
                user.email,
                user.username,
                "password_reset",
                "reset_token",
                ipAddress,
                userAgent
            );
            eventPublisher.publishEvent(passwordChangedEvent);
        } catch (Exception e) {
            LOGGER.warn("Failed to publish password changed event for user: {}", user.email, e);
        }
    }

    public ValidateTokenResponse validateToken(ValidateTokenRequest request) {
        JsonWebToken jwt;
        try {
            // Verifica assinatura (mp.jwt.verify.publickey.location), emissor (mp.jwt.verify.issuer)
            // e expiração. Antes o payload era só decodificado em Base64, sem conferir a assinatura:
            // qualquer token montado à mão com o sub e o email de um usuário passava como válido.
            jwt = jwtParser.parse(request.getToken());
        } catch (ParseException e) {
            LOGGER.debug("Token validation failed: {}", e.getMessage());
            return invalidToken();
        }

        String subject = jwt.getSubject();
        String email = stringClaim(jwt, "email");
        String username = stringClaim(jwt, "username");

        // O refresh token não leva email: só o access token serve para autenticar
        if (subject == null || email == null) {
            return invalidToken();
        }

        // Check if user exists and is active
        User user;
        try {
            user = User.findById(UUID.fromString(subject));
        } catch (IllegalArgumentException e) {
            return invalidToken();
        }
        if (user == null || !Boolean.TRUE.equals(user.isActive)) {
            return invalidToken();
        }

        return new ValidateTokenResponse(true, username, email, List.copyOf(roleNames(user)), subject);
    }

    private static ValidateTokenResponse invalidToken() {
        return new ValidateTokenResponse(false, null, null, null, null);
    }

    private static String stringClaim(JsonWebToken jwt, String name) {
        Object value = jwt.getClaim(name);
        if (value instanceof JsonString jsonString) {
            return jsonString.getString();
        }
        return value != null ? value.toString() : null;
    }

    // NotAuthorizedException(Object, Object...) recebe o challenge do WWW-Authenticate, não a mensagem:
    // com new NotAuthorizedException("Invalid credentials") o getMessage() virava "HTTP 401 Unauthorized"
    // e era isso que chegava no corpo de erro da API.
    private static NotAuthorizedException unauthorized(String message) {
        return new NotAuthorizedException(message, "Bearer");
    }

    // Identificador de sessão dos eventos: o id (UUID) da linha do refresh token, já atribuído no
    // persist(). Não serve para renovar a sessão; o valor do token, que serve, nunca vai para evento.
    private static String sessionId(RefreshToken refreshToken) {
        return refreshToken.id != null ? refreshToken.id.toString() : null;
    }

    // Usuário sem papel atribuído tem roles == null enquanto a entidade não volta do banco.
    private static Set<String> roleNames(User user) {
        if (user.roles == null) {
            return Set.of();
        }
        return user.roles.stream()
            .map(role -> role.name)
            .collect(Collectors.toSet());
    }

    private LoginResponse.UserResponse mapToUserResponse(User user) {
        LoginResponse.UserResponse userResponse = new LoginResponse.UserResponse();
        userResponse.id = user.id.toString();
        userResponse.email = user.email;
        userResponse.username = user.username;
        userResponse.roles = roleNames(user);
        userResponse.lastLogin = user.lastLogin;
        userResponse.isActive = user.isActive;
        userResponse.isVerified = user.isVerified;
        return userResponse;
    }
}