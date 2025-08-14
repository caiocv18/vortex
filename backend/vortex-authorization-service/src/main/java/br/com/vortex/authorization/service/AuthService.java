package br.com.vortex.authorization.service;

import br.com.vortex.authorization.dto.*;
import br.com.vortex.authorization.entity.*;
import br.com.vortex.authorization.event.*;
import br.com.vortex.authorization.security.JwtService;
import br.com.vortex.authorization.security.PasswordService;
import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;

import java.time.OffsetDateTime;
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
    PasswordService passwordService;

    @Inject
    RateLimitService rateLimitService;

    @Inject
    EventPublisher eventPublisher;

    @Inject
    EntityManager entityManager;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        // Check rate limiting
        if (!rateLimitService.isAllowed(request.identifier, ipAddress)) {
            LoginAttempt.recordAttempt(request.identifier, ipAddress, false);
            throw new BadRequestException("Too many failed login attempts. Please try again later.");
        }

        // Find user
        User user = User.findByEmailOrUsername(request.identifier);
        if (user == null || !user.isActiveAndVerified()) {
            LoginAttempt.recordAttempt(request.identifier, ipAddress, false);
            throw new NotAuthorizedException("Invalid credentials");
        }

        // Verify password
        Credential credential = Credential.findByUserId(user.id);
        if (credential == null || !passwordService.verifyPassword(request.password, credential.passwordHash)) {
            LoginAttempt.recordAttempt(request.identifier, ipAddress, false);
            throw new NotAuthorizedException("Invalid credentials");
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
        refreshToken.expiresAt = OffsetDateTime.now().plusDays(7); // TODO: Make configurable
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

        // Publish login event
        try {
            UserLoggedInEvent loginEvent = new UserLoggedInEvent(
                user.id,
                user.email,
                user.username,
                user.roles.stream().map(role -> role.name).collect(Collectors.toSet()),
                "password",
                user.lastLogin,
                refreshTokenValue, // Using refresh token as session ID
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

        // Publish user created event
        try {
            Set<String> roleNames = user.roles != null ? 
                user.roles.stream().map(role -> role.name).collect(Collectors.toSet()) : 
                Set.of();
            UserCreatedEvent userCreatedEvent = new UserCreatedEvent(
                user.id,
                user.email,
                user.username,
                roleNames,
                user.isVerified,
                "registration",
                ipAddress,
                userAgent
            );
            eventPublisher.publishEvent(userCreatedEvent);
        } catch (Exception e) {
            LOGGER.warn("Failed to publish user created event for user: {}", user.email, e);
        }

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

        return response;
    }

    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = RefreshToken.findValidToken(request.refreshToken);
        if (refreshToken == null) {
            throw new NotAuthorizedException("Invalid or expired refresh token");
        }

        User user = refreshToken.user;
        if (!user.isActiveAndVerified()) {
            throw new NotAuthorizedException("User account is not active");
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
                    refreshTokenValue,
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

        // TODO: Send email with reset link
        // emailService.sendPasswordResetEmail(user.email, resetToken.token);
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
        try {
            // Parse JWT without verifying signature first to get claims
            String[] tokenParts = request.getToken().split("\\.");
            if (tokenParts.length != 3) {
                return new ValidateTokenResponse(false, null, null, null, null);
            }

            // Decode payload
            String payload = new String(java.util.Base64.getUrlDecoder().decode(tokenParts[1]));
            
            // Parse JSON claims manually (simple approach)
            Map<String, Object> claims = parseJsonClaims(payload);
            
            String subject = (String) claims.get("sub");
            String email = (String) claims.get("email");
            String username = (String) claims.get("username");
            
            if (subject == null || email == null) {
                return new ValidateTokenResponse(false, null, null, null, null);
            }

            // Check if user exists and is active
            User user = User.findById(UUID.fromString(subject));
            if (user == null || !user.isActive) {
                return new ValidateTokenResponse(false, null, null, null, null);
            }

            // Get user roles
            java.util.List<String> roles = user.roles != null ? 
                user.roles.stream().map(role -> role.name).collect(java.util.stream.Collectors.toList()) :
                java.util.List.of();

            return new ValidateTokenResponse(true, username, email, roles, subject);
            
        } catch (Exception e) {
            LOGGER.debug("Token validation failed: {}", e.getMessage());
            return new ValidateTokenResponse(false, null, null, null, null);
        }
    }

    private Map<String, Object> parseJsonClaims(String json) {
        // Simple JSON parsing for JWT claims
        Map<String, Object> claims = new java.util.HashMap<>();
        
        // Remove braces and split by comma
        json = json.trim().substring(1, json.length() - 1);
        String[] pairs = json.split(",");
        
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim().replace("\"", "");
                claims.put(key, value);
            }
        }
        
        return claims;
    }

    private LoginResponse.UserResponse mapToUserResponse(User user) {
        LoginResponse.UserResponse userResponse = new LoginResponse.UserResponse();
        userResponse.id = user.id.toString();
        userResponse.email = user.email;
        userResponse.username = user.username;
        userResponse.roles = user.roles.stream()
            .map(role -> role.name)
            .collect(Collectors.toSet());
        userResponse.lastLogin = user.lastLogin;
        userResponse.isActive = user.isActive;
        userResponse.isVerified = user.isVerified;
        return userResponse;
    }
}