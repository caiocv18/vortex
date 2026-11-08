package br.com.vortex.authorization.service;

import br.com.vortex.authorization.dto.*;
import br.com.vortex.authorization.entity.*;
import br.com.vortex.authorization.event.*;
import br.com.vortex.authorization.security.JwtService;
import br.com.vortex.authorization.security.PasswordService;
import br.com.vortex.authorization.util.TestDataBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthServiceTest {
    
    @Inject
    AuthService authService;
    
    // Spy, não mock: login e registro fixam os tokens com doReturn, e os testes de validate-token
    // usam a geração real, assinada com a chave de teste (src/test/resources/jwt-test)
    @InjectSpy
    JwtService jwtService;

    @Inject
    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String expectedIssuer;

    @InjectMock
    PasswordService passwordService;
    
    @InjectMock
    RateLimitService rateLimitService;
    
    @InjectMock
    EventPublisher eventPublisher;
    
    @Inject
    EntityManager entityManager;
    
    private static final String TEST_IP = "127.0.0.1";
    private static final String TEST_USER_AGENT = "Test User Agent";
    private static final String TEST_ACCESS_TOKEN = "test.access.token";
    private static final String TEST_REFRESH_TOKEN = "test.refresh.token";
    
    // Os testes usam @TestTransaction (rollback ao fim de cada um): com @Transactional os dados
    // ficavam commitados e o mesmo refresh token "test.refresh.token" violava a unique constraint
    // no teste seguinte.
    @BeforeEach
    void setUp() {
        Mockito.reset(jwtService, passwordService, rateLimitService, eventPublisher);
        // Mock de boolean devolve false: sem este stub o login e o forgot-password eram barrados
        // pelo rate limit. O teste de bloqueio sobrescreve com os argumentos dele.
        when(rateLimitService.isAllowed(anyString(), anyString())).thenReturn(true);
    }
    
    @Test
    @Order(1)
    @DisplayName("Should successfully register a new user with valid data")
    @TestTransaction
    void testRegisterSuccess() {
        // Arrange
        RegisterRequest request = TestDataBuilder.createValidRegisterRequest();
        
        when(passwordService.isValidPassword(request.password)).thenReturn(true);
        when(passwordService.hashPassword(request.password)).thenReturn("hashedPassword");
        doReturn(TEST_ACCESS_TOKEN).when(jwtService).generateAccessToken(any(User.class));
        doReturn(TEST_REFRESH_TOKEN).when(jwtService).generateRefreshToken(any(User.class));
        doReturn(3600L).when(jwtService).getAccessTokenExpirationSeconds();
        
        // Act
        LoginResponse response = authService.register(request, TEST_IP, TEST_USER_AGENT);
        
        // Assert
        assertNotNull(response);
        assertEquals(TEST_ACCESS_TOKEN, response.accessToken);
        assertEquals(TEST_REFRESH_TOKEN, response.refreshToken);
        assertEquals(3600L, response.expiresIn);
        assertNotNull(response.user);
        assertEquals(request.email, response.user.email);
        assertEquals(request.username, response.user.username);
        assertTrue(response.user.isActive);
        assertTrue(response.user.isVerified);
        
        // Verify event publishing
        ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserCreatedEvent event = eventCaptor.getValue();
        assertEquals(request.email, event.userEmail);
        assertEquals(request.username, event.username);
        assertEquals("registration", event.registrationMethod);
        assertEquals(TEST_IP, event.ipAddress);
        // O evento é montado depois da verificação automática: sai com o estado final do usuário
        assertTrue(event.isVerified, "user.created não pode sair com isVerified=false de um usuário já verificado");

        // Verify password was hashed
        verify(passwordService).hashPassword(request.password);
        
        // Verify JWT tokens were generated
        verify(jwtService).generateAccessToken(any(User.class));
        verify(jwtService).generateRefreshToken(any(User.class));
    }
    
    @Test
    @Order(2)
    @DisplayName("Should fail registration when passwords don't match")
    void testRegisterPasswordMismatch() {
        // Arrange
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithMismatchedPasswords();
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.register(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Passwords do not match", exception.getMessage());
        
        // Verify no events were published
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    @Order(3)
    @DisplayName("Should fail registration with weak password")
    void testRegisterWeakPassword() {
        // Arrange
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithWeakPassword();
        String passwordRequirements = "Password must be 8-128 characters long";
        
        when(passwordService.isValidPassword(request.password)).thenReturn(false);
        when(passwordService.getPasswordRequirements()).thenReturn(passwordRequirements);
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.register(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertTrue(exception.getMessage().contains("Password does not meet requirements"));
        assertTrue(exception.getMessage().contains(passwordRequirements));
        
        // Verify no events were published
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    @Order(4)
    @DisplayName("Should fail registration when email already exists")
    @TestTransaction
    void testRegisterDuplicateEmail() {
        // Arrange
        RegisterRequest request = TestDataBuilder.createValidRegisterRequest();
        
        // Create existing user with same email
        User existingUser = TestDataBuilder.createTestUser();
        existingUser.email = request.email;
        existingUser.persist();
        
        when(passwordService.isValidPassword(request.password)).thenReturn(true);
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.register(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Email already registered", exception.getMessage());
        
        // Verify no events were published
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    @Order(5)
    @DisplayName("Should fail registration when username already exists")
    @TestTransaction
    void testRegisterDuplicateUsername() {
        // Arrange
        RegisterRequest request = TestDataBuilder.createValidRegisterRequest();
        
        // Create existing user with same username but different email
        User existingUser = TestDataBuilder.createTestUser();
        existingUser.username = request.username;
        existingUser.email = "different@email.com";
        existingUser.persist();
        
        when(passwordService.isValidPassword(request.password)).thenReturn(true);
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.register(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Username already taken", exception.getMessage());
        
        // Verify no events were published
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    @Order(6)
    @DisplayName("Should successfully login with valid credentials")
    @TestTransaction
    void testLoginSuccess() {
        // Arrange
        String password = "Test@Password123";
        String hashedPassword = "$2a$12$hashedPassword";
        
        User user = TestDataBuilder.createTestUser();
        user.persist();
        
        Credential credential = TestDataBuilder.createCredential(user, hashedPassword);
        credential.persist();
        
        LoginRequest request = TestDataBuilder.createLoginRequest(user.email, password);
        
        when(rateLimitService.isAllowed(request.identifier, TEST_IP)).thenReturn(true);
        when(passwordService.verifyPassword(password, hashedPassword)).thenReturn(true);
        doReturn(TEST_ACCESS_TOKEN).when(jwtService).generateAccessToken(any(User.class));
        doReturn(TEST_REFRESH_TOKEN).when(jwtService).generateRefreshToken(any(User.class));
        doReturn(3600L).when(jwtService).getAccessTokenExpirationSeconds();
        
        // Act
        LoginResponse response = authService.login(request, TEST_IP, TEST_USER_AGENT);
        
        // Assert
        assertNotNull(response);
        assertEquals(TEST_ACCESS_TOKEN, response.accessToken);
        assertEquals(TEST_REFRESH_TOKEN, response.refreshToken);
        assertEquals(3600L, response.expiresIn);
        assertNotNull(response.user);
        assertEquals(user.email, response.user.email);
        assertEquals(user.username, response.user.username);
        
        // Verify event publishing
        ArgumentCaptor<UserLoggedInEvent> eventCaptor = ArgumentCaptor.forClass(UserLoggedInEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserLoggedInEvent event = eventCaptor.getValue();
        assertEquals(user.email, event.userEmail);
        assertEquals("password", event.loginMethod);
        assertEquals(TEST_IP, event.ipAddress);

        // O sessionId é o id da linha do refresh token, nunca o token (credencial de 7 dias)
        RefreshToken stored = RefreshToken.findByToken(TEST_REFRESH_TOKEN);
        assertEquals(stored.id.toString(), event.sessionId);
        assertNotEquals(TEST_REFRESH_TOKEN, event.sessionId);
        assertFalse(event.details.containsValue(TEST_REFRESH_TOKEN), "details também não pode levar o token");

        // Verify rate limiting was checked
        verify(rateLimitService).isAllowed(request.identifier, TEST_IP);
    }
    
    @Test
    @Order(7)
    @DisplayName("Should fail login with invalid password")
    @TestTransaction
    void testLoginInvalidPassword() {
        // Arrange
        String password = "Test@Password123";
        String wrongPassword = "Wrong@Password123";
        String hashedPassword = "$2a$12$hashedPassword";
        
        User user = TestDataBuilder.createTestUser();
        user.persist();
        
        Credential credential = TestDataBuilder.createCredential(user, hashedPassword);
        credential.persist();
        
        LoginRequest request = TestDataBuilder.createLoginRequest(user.email, wrongPassword);
        
        when(rateLimitService.isAllowed(request.identifier, TEST_IP)).thenReturn(true);
        when(passwordService.verifyPassword(wrongPassword, hashedPassword)).thenReturn(false);
        
        // Act & Assert
        NotAuthorizedException exception = assertThrows(
            NotAuthorizedException.class,
            () -> authService.login(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Invalid credentials", exception.getMessage());
        
        // Verify no login event was published
        verify(eventPublisher, never()).publishEvent(any(UserLoggedInEvent.class));
    }
    
    @Test
    @Order(8)
    @DisplayName("Should fail login when user doesn't exist")
    void testLoginUserNotFound() {
        // Arrange
        LoginRequest request = TestDataBuilder.createLoginRequest("nonexistent@email.com", "password");
        
        when(rateLimitService.isAllowed(request.identifier, TEST_IP)).thenReturn(true);
        
        // Act & Assert
        NotAuthorizedException exception = assertThrows(
            NotAuthorizedException.class,
            () -> authService.login(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Invalid credentials", exception.getMessage());
        
        // Verify no events were published
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    @Order(9)
    @DisplayName("Should fail login when user is inactive")
    @TestTransaction
    void testLoginInactiveUser() {
        // Arrange
        User user = TestDataBuilder.createInactiveUser();
        user.persist();
        
        LoginRequest request = TestDataBuilder.createLoginRequest(user.email, "password");
        
        when(rateLimitService.isAllowed(request.identifier, TEST_IP)).thenReturn(true);
        
        // Act & Assert
        NotAuthorizedException exception = assertThrows(
            NotAuthorizedException.class,
            () -> authService.login(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Invalid credentials", exception.getMessage());
        
        // Verify no events were published
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    @Order(10)
    @DisplayName("Should fail login when rate limit is exceeded")
    void testLoginRateLimitExceeded() {
        // Arrange
        LoginRequest request = TestDataBuilder.createLoginRequest("user@email.com", "password");
        
        when(rateLimitService.isAllowed(request.identifier, TEST_IP)).thenReturn(false);
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.login(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertTrue(exception.getMessage().contains("Too many failed login attempts"));
        
        // Verify no events were published
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    @Order(11)
    @DisplayName("Should successfully refresh token with valid refresh token")
    @TestTransaction
    void testRefreshTokenSuccess() {
        // Arrange
        User user = TestDataBuilder.createTestUser();
        user.persist();
        
        RefreshToken refreshToken = TestDataBuilder.createRefreshToken(user, TEST_REFRESH_TOKEN);
        refreshToken.persist();
        
        RefreshTokenRequest request = TestDataBuilder.createRefreshTokenRequest(TEST_REFRESH_TOKEN);
        
        doReturn(TEST_ACCESS_TOKEN).when(jwtService).generateAccessToken(any(User.class));
        doReturn(3600L).when(jwtService).getAccessTokenExpirationSeconds();
        
        // Act
        LoginResponse response = authService.refreshToken(request);
        
        // Assert
        assertNotNull(response);
        assertEquals(TEST_ACCESS_TOKEN, response.accessToken);
        assertEquals(TEST_REFRESH_TOKEN, response.refreshToken);
        assertEquals(3600L, response.expiresIn);
        assertNotNull(response.user);
        assertEquals(user.email, response.user.email);
    }
    
    @Test
    @Order(12)
    @DisplayName("Should fail refresh token when token is invalid")
    void testRefreshTokenInvalid() {
        // Arrange
        RefreshTokenRequest request = TestDataBuilder.createRefreshTokenRequest("invalid.refresh.token");
        
        // Act & Assert
        NotAuthorizedException exception = assertThrows(
            NotAuthorizedException.class,
            () -> authService.refreshToken(request)
        );
        
        assertEquals("Invalid or expired refresh token", exception.getMessage());
    }
    
    @Test
    @Order(13)
    @DisplayName("Should successfully logout and revoke refresh token")
    @TestTransaction
    void testLogoutSuccess() {
        // Arrange
        User user = TestDataBuilder.createTestUser();
        user.persist();
        
        RefreshToken refreshToken = TestDataBuilder.createRefreshToken(user, TEST_REFRESH_TOKEN);
        refreshToken.persist();
        
        // Act
        authService.logout(TEST_REFRESH_TOKEN, TEST_IP, TEST_USER_AGENT);
        
        // Assert - Verify logout event was published
        ArgumentCaptor<UserLoggedOutEvent> eventCaptor = ArgumentCaptor.forClass(UserLoggedOutEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        UserLoggedOutEvent event = eventCaptor.getValue();
        assertEquals(user.email, event.userEmail);
        assertEquals("user_initiated", event.logoutReason);
        assertEquals(TEST_IP, event.ipAddress);
        assertEquals(refreshToken.id.toString(), event.sessionId);
        assertNotEquals(TEST_REFRESH_TOKEN, event.sessionId);

        // Verify token was revoked
        entityManager.flush();
        entityManager.clear();
        RefreshToken revokedToken = RefreshToken.findById(refreshToken.id);
        assertTrue(revokedToken.revoked);
    }
    
    @Test
    @Order(14)
    @DisplayName("Should handle forgot password request successfully")
    @TestTransaction
    void testForgotPasswordSuccess() {
        // Arrange
        User user = TestDataBuilder.createTestUser();
        user.persist();
        
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest(user.email);
        
        when(passwordService.generateSecureToken(32)).thenReturn("resetToken123");
        
        // Act
        authService.forgotPassword(request, TEST_IP, TEST_USER_AGENT);
        
        // Assert - Verify event was published
        ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor = 
            ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PasswordResetRequestedEvent event = eventCaptor.getValue();
        assertEquals(user.email, event.userEmail);
        assertEquals("email", event.requestMethod);
        assertEquals(TEST_IP, event.ipAddress);
    }
    
    @Test
    @Order(15)
    @DisplayName("Should silently ignore forgot password for non-existent email")
    void testForgotPasswordNonExistentEmail() {
        // Arrange
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest("nonexistent@email.com");
        
        // Act - Should not throw exception
        assertDoesNotThrow(() -> authService.forgotPassword(request, TEST_IP, TEST_USER_AGENT));
        
        // Assert - No events should be published
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    @Order(16)
    @DisplayName("Should successfully reset password with valid token")
    @TestTransaction
    void testResetPasswordSuccess() {
        // Arrange
        User user = TestDataBuilder.createTestUser();
        user.persist();
        
        Credential credential = TestDataBuilder.createCredential(user, "oldHashedPassword");
        credential.persist();
        
        PasswordResetToken resetToken = TestDataBuilder.createPasswordResetToken(user, "resetToken123");
        resetToken.persist();
        
        String newPassword = "NewPassword@123";
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest("resetToken123", newPassword);
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);
        when(passwordService.hashPassword(newPassword)).thenReturn("newHashedPassword");
        
        // Act
        authService.resetPassword(request, TEST_IP, TEST_USER_AGENT);
        
        // Assert - Verify password was updated
        verify(passwordService).hashPassword(newPassword);
        
        // Verify event was published
        ArgumentCaptor<PasswordChangedEvent> eventCaptor = ArgumentCaptor.forClass(PasswordChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PasswordChangedEvent event = eventCaptor.getValue();
        assertEquals(user.email, event.userEmail);
        assertEquals("password_reset", event.changeReason);
        assertEquals(TEST_IP, event.ipAddress);
        
        // Verify token was marked as used
        entityManager.flush();
        entityManager.clear();
        PasswordResetToken usedToken = PasswordResetToken.findById(resetToken.id);
        assertTrue(usedToken.used);
    }
    
    @Test
    @Order(17)
    @DisplayName("Should fail reset password with invalid token")
    void testResetPasswordInvalidToken() {
        // Arrange
        String newPassword = "NewPassword@123";
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest("invalidToken", newPassword);
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.resetPassword(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Invalid or expired reset token", exception.getMessage());
        
        // Verify no events were published
        verify(eventPublisher, never()).publishEvent(any());
    }
    
    @Test
    @Order(18)
    @DisplayName("Should validate token and return user information")
    @TestTransaction
    void testValidateTokenSuccess() {
        // Arrange
        String email = "user@test.com";
        String username = "testuser";

        // O id é @GeneratedValue: persiste primeiro e monta o token com o id gerado
        // (atribuir um UUID antes do persist faz o Hibernate tratar a entidade como detached).
        User user = TestDataBuilder.createTestUser();
        user.email = email;
        user.username = username;

        // roles.name é único e o papel USER já vem do import.sql (ou de outro teste)
        Role userRole = Role.findUserRole();
        if (userRole == null) {
            userRole = TestDataBuilder.createUserRole();
            userRole.persist();
        }
        user.roles = Set.of(userRole);
        user.persist();

        String userId = user.id.toString();
        // Token emitido pelo próprio JwtService (spy sem stub = método real), assinado com a chave
        // de teste. O token montado à mão que ficava aqui tinha assinatura de mentira e só passava
        // porque o validateToken não conferia a assinatura (ver testValidateTokenRejectsForgedSignature).
        String tokenString = jwtService.generateAccessToken(user);

        ValidateTokenRequest request = TestDataBuilder.createValidateTokenRequest(tokenString);
        
        // Act
        ValidateTokenResponse response = authService.validateToken(request);
        
        // Assert
        assertTrue(response.isValid());
        assertEquals(username, response.getUsername());
        assertEquals(email, response.getEmail());
        assertEquals(userId, response.getUserId());
        assertTrue(response.getRoles().contains("USER"));
    }
    
    @Test
    @Order(19)
    @DisplayName("Should return invalid response for malformed token")
    void testValidateTokenMalformed() {
        // Arrange
        ValidateTokenRequest request = TestDataBuilder.createValidateTokenRequest("malformed.token");
        
        // Act
        ValidateTokenResponse response = authService.validateToken(request);
        
        // Assert
        assertFalse(response.isValid());
        assertNull(response.getUsername());
        assertNull(response.getEmail());
        assertNull(response.getUserId());
        assertNull(response.getRoles());
    }

    @Test
    @Order(20)
    @DisplayName("Should reject tokens not signed with the service key")
    @TestTransaction
    void testValidateTokenRejectsForgedSignature() throws Exception {
        User user = persistActiveUserWithUserRole();
        String userId = user.id.toString();

        // O token legítimo passa: o que muda nos casos abaixo é só a assinatura
        String genuine = jwtService.generateAccessToken(user);
        assertTrue(validate(genuine).isValid());

        // 1. Token montado à mão com assinatura de mentira (o que o validateToken antigo aceitava)
        assertInvalid(createMockJwtToken(userId, user.email, user.username));

        // 2. Mesmas claims e emissor certo, assinado com outra chave RSA
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        PrivateKey foreignKey = generator.generateKeyPair().getPrivate();
        assertInvalid(accessTokenClaims(user, expectedIssuer)
            .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
            .sign(foreignKey));

        // 3. Token legítimo com o payload adulterado (outro e-mail) e a assinatura original
        String[] parts = genuine.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
            .replace(user.email, "attacker@evil.example");
        String tampered = parts[0] + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
            + "." + parts[2];
        assertInvalid(tampered);
    }

    @Test
    @Order(21)
    @DisplayName("Should reject an expired token signed with the service key")
    @TestTransaction
    void testValidateTokenRejectsExpiredToken() {
        User user = persistActiveUserWithUserRole();

        // Assinatura e emissor corretos; venceu há 1 hora (bem além da tolerância de relógio)
        String expired = accessTokenClaims(user, expectedIssuer)
            .issuedAt(Instant.now().minus(2, ChronoUnit.HOURS))
            .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
            .sign();

        assertInvalid(expired);
    }

    @Test
    @Order(22)
    @DisplayName("Should reject a token from another issuer signed with the service key")
    @TestTransaction
    void testValidateTokenRejectsWrongIssuer() {
        User user = persistActiveUserWithUserRole();
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);

        // Controle: as mesmas claims com o emissor configurado passam
        assertTrue(validate(accessTokenClaims(user, expectedIssuer).expiresAt(expiresAt).sign()).isValid());

        assertInvalid(accessTokenClaims(user, "https://attacker.example").expiresAt(expiresAt).sign());
    }

    @Test
    @Order(23)
    @DisplayName("Should reject a refresh token: only access tokens authenticate")
    @TestTransaction
    void testValidateTokenRejectsRefreshToken() {
        User user = persistActiveUserWithUserRole();

        assertInvalid(jwtService.generateRefreshToken(user));
    }

    private User persistActiveUserWithUserRole() {
        User user = TestDataBuilder.createTestUser();
        Role userRole = Role.findUserRole();
        if (userRole == null) {
            userRole = TestDataBuilder.createUserRole();
            userRole.persist();
        }
        user.roles = Set.of(userRole);
        user.persist();
        return user;
    }

    // Mesmas claims que o JwtService.generateAccessToken grava, com o emissor escolhido pelo teste
    private JwtClaimsBuilder accessTokenClaims(User user, String issuer) {
        return Jwt.issuer(issuer)
            .upn(user.email)
            .subject(user.id.toString())
            .claim("username", user.username)
            .claim("email", user.email)
            .claim("roles", Set.of("USER"));
    }

    private ValidateTokenResponse validate(String token) {
        return authService.validateToken(TestDataBuilder.createValidateTokenRequest(token));
    }

    private void assertInvalid(String token) {
        ValidateTokenResponse response = validate(token);
        assertFalse(response.isValid());
        assertNull(response.getUsername());
        assertNull(response.getEmail());
        assertNull(response.getUserId());
        assertNull(response.getRoles());
    }

    private String createMockJwtToken(String userId, String email, String username) {
        // Create a simple mock JWT token structure (header.payload.signature)
        String header = java.util.Base64.getUrlEncoder().encodeToString("{}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().encodeToString(
            String.format("{\"sub\":\"%s\",\"email\":\"%s\",\"username\":\"%s\"}", 
                userId, email, username).getBytes()
        );
        String signature = java.util.Base64.getUrlEncoder().encodeToString("signature".getBytes());
        return header + "." + payload + "." + signature;
    }
}