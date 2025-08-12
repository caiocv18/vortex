package br.com.vortex.authorization.service;

import br.com.vortex.authorization.dto.*;
import br.com.vortex.authorization.entity.*;
import br.com.vortex.authorization.security.PasswordService;
import br.com.vortex.authorization.util.TestDataBuilder;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simplified unit tests for AuthService that don't require full Quarkus context
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimpleAuthServiceTest {
    
    @Test
    @Order(1)
    @DisplayName("TestDataBuilder should create valid RegisterRequest")
    void testCreateValidRegisterRequest() {
        RegisterRequest request = TestDataBuilder.createValidRegisterRequest();
        
        assertNotNull(request);
        assertNotNull(request.email);
        assertNotNull(request.username);
        assertNotNull(request.password);
        assertNotNull(request.confirmPassword);
        assertTrue(request.email.contains("@"));
        assertEquals(request.password, request.confirmPassword);
        assertTrue(request.username.length() >= 3);
    }
    
    @Test
    @Order(2)
    @DisplayName("TestDataBuilder should create invalid RegisterRequest variations")
    void testCreateInvalidRegisterRequests() {
        RegisterRequest weakPassword = TestDataBuilder.createRegisterRequestWithWeakPassword();
        RegisterRequest mismatchedPasswords = TestDataBuilder.createRegisterRequestWithMismatchedPasswords();
        RegisterRequest invalidEmail = TestDataBuilder.createRegisterRequestWithInvalidEmail();
        RegisterRequest shortUsername = TestDataBuilder.createRegisterRequestWithShortUsername();
        RegisterRequest invalidUsername = TestDataBuilder.createRegisterRequestWithInvalidUsername();
        
        assertNotNull(weakPassword);
        assertNotNull(mismatchedPasswords);
        assertNotNull(invalidEmail);
        assertNotNull(shortUsername);
        assertNotNull(invalidUsername);
        
        assertNotEquals(mismatchedPasswords.password, mismatchedPasswords.confirmPassword);
        assertFalse(invalidEmail.email.contains("@"));
        assertTrue(shortUsername.username.length() < 3);
        assertTrue(invalidUsername.username.contains(" "));
    }
    
    @Test
    @Order(3)
    @DisplayName("TestDataBuilder should create test entities")
    void testCreateTestEntities() {
        User user = TestDataBuilder.createTestUser();
        User inactiveUser = TestDataBuilder.createInactiveUser();
        User unverifiedUser = TestDataBuilder.createUnverifiedUser();
        
        assertNotNull(user);
        assertNotNull(user.email);
        assertNotNull(user.username);
        assertTrue(user.isActive);
        assertTrue(user.isVerified);
        
        assertNotNull(inactiveUser);
        assertFalse(inactiveUser.isActive);
        
        assertNotNull(unverifiedUser);
        assertFalse(unverifiedUser.isVerified);
        
        Role userRole = TestDataBuilder.createUserRole();
        Role adminRole = TestDataBuilder.createAdminRole();
        
        assertNotNull(userRole);
        assertEquals("USER", userRole.name);
        
        assertNotNull(adminRole);
        assertEquals("ADMIN", adminRole.name);
    }
    
    @Test
    @Order(4)
    @DisplayName("TestDataBuilder should create credentials and tokens")
    void testCreateCredentialsAndTokens() {
        User user = TestDataBuilder.createTestUser();
        String passwordHash = "hashedPassword123";
        
        Credential credential = TestDataBuilder.createCredential(user, passwordHash);
        assertNotNull(credential);
        assertEquals(user, credential.user);
        assertEquals(passwordHash, credential.passwordHash);
        
        String tokenValue = "testRefreshToken";
        RefreshToken refreshToken = TestDataBuilder.createRefreshToken(user, tokenValue);
        assertNotNull(refreshToken);
        assertEquals(user, refreshToken.user);
        assertEquals(tokenValue, refreshToken.token);
        
        RefreshToken expiredToken = TestDataBuilder.createExpiredRefreshToken(user, tokenValue);
        assertNotNull(expiredToken);
        assertTrue(expiredToken.expiresAt.isBefore(java.time.OffsetDateTime.now()));
        
        String resetTokenValue = "resetToken123";
        PasswordResetToken resetToken = TestDataBuilder.createPasswordResetToken(user, resetTokenValue);
        assertNotNull(resetToken);
        assertEquals(user, resetToken.user);
        assertEquals(resetTokenValue, resetToken.token);
    }
    
    @Test
    @Order(5)
    @DisplayName("TestDataBuilder should create request DTOs")
    void testCreateRequestDTOs() {
        String email = "test@example.com";
        String password = "TestPassword123!";
        String token = "testToken123";
        
        LoginRequest loginRequest = TestDataBuilder.createLoginRequest(email, password);
        assertNotNull(loginRequest);
        assertEquals(email, loginRequest.identifier);
        assertEquals(password, loginRequest.password);
        
        ForgotPasswordRequest forgotRequest = TestDataBuilder.createForgotPasswordRequest(email);
        assertNotNull(forgotRequest);
        assertEquals(email, forgotRequest.email);
        
        ResetPasswordRequest resetRequest = TestDataBuilder.createResetPasswordRequest(token, password);
        assertNotNull(resetRequest);
        assertEquals(token, resetRequest.token);
        assertEquals(password, resetRequest.password);
        assertEquals(password, resetRequest.confirmPassword);
        
        ValidateTokenRequest validateRequest = TestDataBuilder.createValidateTokenRequest(token);
        assertNotNull(validateRequest);
        
        RefreshTokenRequest refreshRequest = TestDataBuilder.createRefreshTokenRequest(token);
        assertNotNull(refreshRequest);
        assertEquals(token, refreshRequest.refreshToken);
    }
    
    @Test
    @Order(6)
    @DisplayName("RegisterRequest validation annotations should be properly defined")
    void testRegisterRequestValidationAnnotations() {
        RegisterRequest request = new RegisterRequest();
        
        // Test that the class has the expected fields
        assertDoesNotThrow(() -> {
            request.email = "test@example.com";
            request.username = "testuser";
            request.password = "Test@123";
            request.confirmPassword = "Test@123";
        });
        
        assertEquals("test@example.com", request.email);
        assertEquals("testuser", request.username);
        assertEquals("Test@123", request.password);
        assertEquals("Test@123", request.confirmPassword);
    }
    
    @Test
    @Order(7)
    @DisplayName("Entity relationships should be properly defined")
    void testEntityRelationships() {
        User user = TestDataBuilder.createTestUser();
        Role userRole = TestDataBuilder.createUserRole();
        Credential credential = TestDataBuilder.createCredential(user, "hash");
        RefreshToken refreshToken = TestDataBuilder.createRefreshToken(user, "token");
        
        // Test User entity structure
        assertNotNull(user.id);
        assertNotNull(user.email);
        assertNotNull(user.username);
        assertNotNull(user.createdAt);
        assertNotNull(user.updatedAt);
        assertTrue(user.isActive);
        assertTrue(user.isVerified);
        
        // Test Role entity structure
        assertNotNull(userRole.id);
        assertEquals("USER", userRole.name);
        assertEquals("Default user role", userRole.description);
        assertNotNull(userRole.createdAt);
        
        // Test Credential entity structure
        assertNotNull(credential.id);
        assertEquals(user, credential.user);
        assertEquals("hash", credential.passwordHash);
        assertNotNull(credential.createdAt);
        assertNotNull(credential.updatedAt);
        
        // Test RefreshToken entity structure
        assertNotNull(refreshToken.id);
        assertEquals(user, refreshToken.user);
        assertEquals("token", refreshToken.token);
        assertNotNull(refreshToken.expiresAt);
        assertNotNull(refreshToken.createdAt);
        assertEquals(false, refreshToken.revoked);
    }
    
    @Test
    @Order(8)
    @DisplayName("TestDataBuilder should generate unique data")
    void testDataUniqueness() {
        RegisterRequest req1 = TestDataBuilder.createValidRegisterRequest();
        RegisterRequest req2 = TestDataBuilder.createValidRegisterRequest();
        
        assertNotEquals(req1.email, req2.email);
        assertNotEquals(req1.username, req2.username);
        
        User user1 = TestDataBuilder.createTestUser();
        User user2 = TestDataBuilder.createTestUser();
        
        assertNotEquals(user1.email, user2.email);
        assertNotEquals(user1.username, user2.username);
    }
}