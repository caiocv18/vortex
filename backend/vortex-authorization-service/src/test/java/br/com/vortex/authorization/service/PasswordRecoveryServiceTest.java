package br.com.vortex.authorization.service;

import br.com.vortex.authorization.dto.*;
import br.com.vortex.authorization.entity.*;
import br.com.vortex.authorization.event.*;
import br.com.vortex.authorization.security.PasswordService;
import br.com.vortex.authorization.util.TestDataBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PasswordRecoveryServiceTest {

    private static final String TEST_IP = "192.168.1.100";
    private static final String TEST_USER_AGENT = "Mozilla/5.0 Test Browser";

    @Inject
    AuthService authService;

    @InjectMock
    PasswordService passwordService;

    @InjectMock
    EventPublisher eventPublisher;

    private User testUser;
    private Credential testCredential;
    
    // Helper method to generate unique tokens
    private String generateUniqueToken(String prefix) {
        return prefix + System.currentTimeMillis() + "_" + System.nanoTime();
    }

    @BeforeEach
    @Transactional
    void setupEachTest() {
        // Create test user with unique email to avoid conflicts
        String uniqueEmail = "testuser" + System.currentTimeMillis() + "@test.com";
        testUser = new User();
        testUser.email = uniqueEmail;
        testUser.username = "testuser" + System.currentTimeMillis();
        testUser.isActive = true;
        testUser.isVerified = true;
        testUser.createdAt = OffsetDateTime.now();
        testUser.updatedAt = OffsetDateTime.now();
        testUser.persist();
        
        // Create credential for user
        String hashedPassword = "$2a$12$hashedPasswordExample123456789";
        testCredential = new Credential();
        testCredential.user = testUser;
        testCredential.passwordHash = hashedPassword;
        testCredential.createdAt = OffsetDateTime.now();
        testCredential.updatedAt = OffsetDateTime.now();
        testCredential.persist();
        
        // Reset mocks
        reset(passwordService, eventPublisher);
    }

    @Test
    @Order(1)
    @DisplayName("Should successfully request password reset for existing user")
    @Transactional
    void testForgotPasswordSuccess() {
        // Arrange
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest(testUser.email);
        String expectedToken = generateUniqueToken("secureResetToken");
        
        when(passwordService.generateSecureToken(32)).thenReturn(expectedToken);
        
        // Act
        authService.forgotPassword(request, TEST_IP, TEST_USER_AGENT);
        
        // Assert
        verify(passwordService).generateSecureToken(32);
        
        // Verify token was created
        PasswordResetToken createdToken = PasswordResetToken.findByToken(expectedToken);
        assertNotNull(createdToken);
        assertEquals(testUser.id, createdToken.user.id);
        assertEquals(expectedToken, createdToken.token);
        assertFalse(createdToken.used);
        assertTrue(createdToken.expiresAt.isAfter(OffsetDateTime.now()));
        assertTrue(createdToken.expiresAt.isBefore(OffsetDateTime.now().plusHours(2)));
        
        // Verify event publication
        ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor = 
            ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        PasswordResetRequestedEvent event = eventCaptor.getValue();
        assertEquals(testUser.id, event.userId);
        assertEquals(testUser.email, event.userEmail);
        assertEquals(testUser.username, event.username);
        assertEquals(createdToken.id, event.resetTokenId);
        assertEquals("email", event.requestMethod);
        assertEquals(TEST_IP, event.ipAddress);
    }

    @Test
    @Order(2)
    @DisplayName("Should silently handle password reset request for non-existent email")
    @Transactional
    void testForgotPasswordNonExistentEmail() {
        // Arrange
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest("nonexistent@email.com");
        
        // Act - Should not throw exception
        assertDoesNotThrow(() -> authService.forgotPassword(request, TEST_IP, TEST_USER_AGENT));
        
        // Assert - No token should be generated
        verify(passwordService, never()).generateSecureToken(anyInt());
        
        // No events should be published
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @Order(3)
    @DisplayName("Should silently handle password reset request for inactive user")
    @Transactional
    void testForgotPasswordInactiveUser() {
        // Arrange
        User inactiveUser = TestDataBuilder.createInactiveUser();
        inactiveUser.persist();
        
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest(inactiveUser.email);
        
        // Act
        assertDoesNotThrow(() -> authService.forgotPassword(request, TEST_IP, TEST_USER_AGENT));
        
        // Assert - No token should be generated
        verify(passwordService, never()).generateSecureToken(anyInt());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @Order(4)
    @DisplayName("Should expire existing tokens when creating new reset token")
    @Transactional
    void testForgotPasswordExpiresExistingTokens() {
        // Arrange - Create existing token
        String oldTokenValue = generateUniqueToken("oldToken");
        PasswordResetToken existingToken = TestDataBuilder.createPasswordResetToken(testUser, oldTokenValue);
        existingToken.persist();
        
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest(testUser.email);
        String newTokenValue = generateUniqueToken("newToken");
        when(passwordService.generateSecureToken(32)).thenReturn(newTokenValue);
        
        // Act
        authService.forgotPassword(request, TEST_IP, TEST_USER_AGENT);
        
        // Force flush to ensure database updates are visible
        PasswordResetToken.flush();
        
        // Assert - Old token should be marked as used
        existingToken = PasswordResetToken.findByToken(oldTokenValue);
        assertTrue(existingToken.used);
        
        // New token should be valid
        PasswordResetToken newToken = PasswordResetToken.findByToken(newTokenValue);
        assertNotNull(newToken);
        assertFalse(newToken.used);
    }

    @Test
    @Order(5)
    @DisplayName("Should successfully reset password with valid token")
    @Transactional
    void testResetPasswordSuccess() {
        // Arrange
        String resetToken = generateUniqueToken("validResetToken");
        String newPassword = "NewValidPassword@123";
        String hashedNewPassword = "$2a$12$newHashedPassword";
        
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, resetToken);
        token.persist();
        
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(resetToken, newPassword);
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);
        when(passwordService.hashPassword(newPassword)).thenReturn(hashedNewPassword);
        
        // Act
        authService.resetPassword(request, TEST_IP, TEST_USER_AGENT);
        
        // Assert
        verify(passwordService).isValidPassword(newPassword);
        verify(passwordService).hashPassword(newPassword);
        
        // Token should be marked as used
        PasswordResetToken usedToken = PasswordResetToken.findByToken(resetToken);
        assertTrue(usedToken.used);
        
        // Password should be updated
        Credential updatedCredential = Credential.findByUserId(testUser.id);
        assertEquals(hashedNewPassword, updatedCredential.passwordHash);
        
        // Verify event publication
        ArgumentCaptor<PasswordChangedEvent> eventCaptor = 
            ArgumentCaptor.forClass(PasswordChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        PasswordChangedEvent event = eventCaptor.getValue();
        assertEquals(testUser.id, event.userId);
        assertEquals(testUser.email, event.userEmail);
        assertEquals(testUser.username, event.username);
        assertEquals("password_reset", event.changeReason);
        assertEquals("reset_token", event.changeMethod);
    }

    @Test
    @Order(6)
    @DisplayName("Should reject password reset with mismatched passwords")
    @Transactional
    void testResetPasswordMismatchedPasswords() {
        // Arrange
        String resetToken = generateUniqueToken("validToken");
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, resetToken);
        token.persist();
        
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.token = resetToken;
        request.password = "Password@123";
        request.confirmPassword = "DifferentPassword@123";
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.resetPassword(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Passwords do not match", exception.getMessage());
        
        // No password service calls should be made
        verify(passwordService, never()).isValidPassword(anyString());
        verify(passwordService, never()).hashPassword(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @Order(7)
    @DisplayName("Should reject password reset with invalid password policy")
    @Transactional
    void testResetPasswordInvalidPasswordPolicy() {
        // Arrange
        String resetToken = generateUniqueToken("validToken");
        String weakPassword = "weak";
        
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, resetToken);
        token.persist();
        
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(resetToken, weakPassword);
        
        when(passwordService.isValidPassword(weakPassword)).thenReturn(false);
        when(passwordService.getPasswordRequirements()).thenReturn("Password must meet security requirements");
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.resetPassword(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertTrue(exception.getMessage().contains("Password does not meet requirements"));
        
        // Password should not be hashed
        verify(passwordService, never()).hashPassword(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @Order(8)
    @DisplayName("Should reject password reset with invalid token")
    @Transactional
    void testResetPasswordInvalidToken() {
        // Arrange
        String invalidToken = generateUniqueToken("nonExistentToken");
        String newPassword = "ValidPassword@123";
        
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(invalidToken, newPassword);
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.resetPassword(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Invalid or expired reset token", exception.getMessage());
        
        // No password operations should occur
        verify(passwordService, never()).hashPassword(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @Order(9)
    @DisplayName("Should reject password reset with expired token")
    @Transactional
    void testResetPasswordExpiredToken() {
        // Arrange
        String expiredToken = generateUniqueToken("expiredToken");
        String newPassword = "ValidPassword@123";
        
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, expiredToken);
        token.expiresAt = OffsetDateTime.now().minusHours(2); // Expired 2 hours ago
        token.persist();
        
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(expiredToken, newPassword);
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.resetPassword(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Invalid or expired reset token", exception.getMessage());
        
        // No password operations should occur
        verify(passwordService, never()).hashPassword(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @Order(10)
    @DisplayName("Should reject password reset with already used token")
    @Transactional
    void testResetPasswordUsedToken() {
        // Arrange
        String usedToken = generateUniqueToken("usedToken");
        String newPassword = "ValidPassword@123";
        
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, usedToken);
        token.used = true; // Mark as already used
        token.persist();
        
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(usedToken, newPassword);
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.resetPassword(request, TEST_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Invalid or expired reset token", exception.getMessage());
        
        // No password operations should occur
        verify(passwordService, never()).hashPassword(anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @Order(11)
    @DisplayName("Should validate password reset token correctly")
    @Transactional
    void testPasswordResetTokenValidation() {
        // Arrange
        String validToken = generateUniqueToken("validToken");
        String invalidToken = generateUniqueToken("invalidToken");
        
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, validToken);
        token.persist();
        
        // Act & Assert
        PasswordResetToken foundValid = PasswordResetToken.findValidToken(validToken);
        PasswordResetToken foundInvalid = PasswordResetToken.findValidToken(invalidToken);
        
        assertNotNull(foundValid);
        assertEquals(validToken, foundValid.token);
        assertTrue(foundValid.isValid());
        
        assertNull(foundInvalid);
    }

    @Test
    @Order(12)
    @DisplayName("Should handle token expiration edge cases")
    @Transactional
    void testTokenExpirationEdgeCases() {
        // Arrange - Token expiring in 1 minute (edge case)
        String almostExpiredToken = generateUniqueToken("almostExpiredToken");
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, almostExpiredToken);
        token.expiresAt = OffsetDateTime.now().plusMinutes(1);
        token.persist();
        
        // Act
        PasswordResetToken foundToken = PasswordResetToken.findValidToken(almostExpiredToken);
        
        // Assert - Should still be valid
        assertNotNull(foundToken);
        assertTrue(foundToken.isValid());
        
        // Test with token that just expired
        token.expiresAt = OffsetDateTime.now().minusSeconds(1);
        token.persist();
        
        PasswordResetToken expiredToken = PasswordResetToken.findValidToken(almostExpiredToken);
        assertNull(expiredToken);
    }

    @Test
    @Order(13)
    @DisplayName("Should handle multiple concurrent password reset requests")
    @Transactional
    void testConcurrentPasswordResetRequests() {
        // Arrange
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest(testUser.email);
        
        String firstTokenValue = generateUniqueToken("token1");
        String secondTokenValue = generateUniqueToken("token2");
        
        when(passwordService.generateSecureToken(32))
            .thenReturn(firstTokenValue)
            .thenReturn(secondTokenValue);
        
        // Act - Simulate concurrent requests
        authService.forgotPassword(request, TEST_IP, TEST_USER_AGENT);
        authService.forgotPassword(request, TEST_IP, TEST_USER_AGENT);
        
        // Force flush to ensure database updates are visible
        PasswordResetToken.flush();
        
        // Assert - Both tokens should be generated but first should be expired
        verify(passwordService, times(2)).generateSecureToken(32);
        
        PasswordResetToken firstToken = PasswordResetToken.findByToken(firstTokenValue);
        PasswordResetToken secondToken = PasswordResetToken.findByToken(secondTokenValue);
        
        assertNotNull(firstToken);
        assertNotNull(secondToken);
        assertTrue(firstToken.used); // First token should be expired
        assertFalse(secondToken.used); // Second token should be active
        
        // Two events should be published
        verify(eventPublisher, times(2)).publishEvent(any(PasswordResetRequestedEvent.class));
    }

    @Test
    @Order(14)
    @DisplayName("Should clean up expired tokens efficiently")
    @Transactional
    void testExpiredTokenCleanup() {
        // Arrange - Create multiple tokens for the user
        String activeTokenValue = generateUniqueToken("activeToken");
        PasswordResetToken activeToken = TestDataBuilder.createPasswordResetToken(testUser, activeTokenValue);
        activeToken.persist();
        
        String expiredToken1Value = generateUniqueToken("expiredToken1");
        PasswordResetToken expiredToken1 = TestDataBuilder.createPasswordResetToken(testUser, expiredToken1Value);
        expiredToken1.expiresAt = OffsetDateTime.now().minusHours(2);
        expiredToken1.persist();
        
        String expiredToken2Value = generateUniqueToken("expiredToken2");
        PasswordResetToken expiredToken2 = TestDataBuilder.createPasswordResetToken(testUser, expiredToken2Value);
        expiredToken2.expiresAt = OffsetDateTime.now().minusHours(1);
        expiredToken2.persist();
        
        // Act - Request new password reset (should expire all existing tokens)
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest(testUser.email);
        String newActiveTokenValue = generateUniqueToken("newActiveToken");
        when(passwordService.generateSecureToken(32)).thenReturn(newActiveTokenValue);
        
        authService.forgotPassword(request, TEST_IP, TEST_USER_AGENT);
        
        // Force flush to ensure database updates are visible
        PasswordResetToken.flush();
        
        // Assert - All old tokens should be marked as used
        PasswordResetToken expiredActive = PasswordResetToken.findByToken(activeTokenValue);
        PasswordResetToken stillExpired1 = PasswordResetToken.findByToken(expiredToken1Value);
        PasswordResetToken stillExpired2 = PasswordResetToken.findByToken(expiredToken2Value);
        PasswordResetToken newActive = PasswordResetToken.findByToken(newActiveTokenValue);
        
        assertTrue(expiredActive.used);
        assertTrue(stillExpired1.used);
        assertTrue(stillExpired2.used);
        assertFalse(newActive.used);
    }

    @Test
    @Order(15)
    @DisplayName("Should handle password reset with existing credential")
    @Transactional
    void testResetPasswordWithExistingCredential() {
        // Arrange - Create user with credential
        User userWithCredential = TestDataBuilder.createTestUser();
        userWithCredential.persist();
        
        // Create credential for the user
        Credential credential = TestDataBuilder.createCredential(userWithCredential, "$2a$12$oldHashedPassword");
        credential.persist();
        
        String resetToken = generateUniqueToken("validToken");
        String newPassword = "ValidPassword@123";
        String hashedPassword = "$2a$12$newHashedPassword";
        
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(userWithCredential, resetToken);
        token.persist();
        
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(resetToken, newPassword);
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);
        when(passwordService.hashPassword(newPassword)).thenReturn(hashedPassword);
        
        // Act
        authService.resetPassword(request, TEST_IP, TEST_USER_AGENT);
        
        // Assert - Credential should be updated
        Credential updatedCredential = Credential.findByUserId(userWithCredential.id);
        assertNotNull(updatedCredential);
        assertEquals(hashedPassword, updatedCredential.passwordHash);
        assertEquals(userWithCredential.id, updatedCredential.user.id);
        
        // Token should be marked as used
        PasswordResetToken usedToken = PasswordResetToken.findByToken(resetToken);
        assertTrue(usedToken.used);
        
        // Event should be published
        verify(eventPublisher).publishEvent(any(PasswordChangedEvent.class));
    }
}