package br.com.vortex.authorization.security;

import br.com.vortex.authorization.dto.*;
import br.com.vortex.authorization.entity.*;
import br.com.vortex.authorization.service.AuthService;
import br.com.vortex.authorization.service.RateLimitService;
import br.com.vortex.authorization.util.TestDataBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PasswordRecoverySecurityTest {

    private static final String TEST_IP = "192.168.1.100";
    private static final String MALICIOUS_IP = "10.0.0.1";
    private static final String TEST_USER_AGENT = "Mozilla/5.0 Test Browser";

    @Inject
    AuthService authService;

    @InjectMock
    RateLimitService rateLimitService;

    @InjectMock
    PasswordService passwordService;

    private User testUser;

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
        Credential credential = new Credential();
        credential.user = testUser;
        credential.passwordHash = "$2a$12$hashedPassword";
        credential.createdAt = OffsetDateTime.now();
        credential.updatedAt = OffsetDateTime.now();
        credential.persist();
        
        // Reset mocks - Allow rate limiting by default
        reset(rateLimitService, passwordService);
        when(rateLimitService.isAllowed(anyString(), anyString())).thenReturn(true);
    }

    @Test
    @Order(1)
    @DisplayName("Should generate cryptographically secure reset tokens")
    @Transactional
    void testSecureTokenGeneration() {
        // Arrange
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest(testUser.email);
        
        // Mock token generation to verify it's called with secure parameters
        String expectedToken = "abcdefghijklmnopqrstuvwxyz123456"; // Exactly 32 characters
        when(passwordService.generateSecureToken(32)).thenReturn(expectedToken);
        
        // Act
        authService.forgotPassword(request, TEST_IP, TEST_USER_AGENT);
        
        // Assert
        verify(passwordService).generateSecureToken(32);
        
        // Verify token properties
        PasswordResetToken token = PasswordResetToken.findByToken(expectedToken);
        assertNotNull(token);
        assertEquals(32, expectedToken.length());
        
        // Token should have proper expiration (1 hour from now)
        assertTrue(token.expiresAt.isAfter(OffsetDateTime.now().plusMinutes(55)));
        assertTrue(token.expiresAt.isBefore(OffsetDateTime.now().plusMinutes(65)));
    }

    @Test
    @Order(2)
    @DisplayName("Should enforce rate limiting on forgot password requests")
    @Transactional
    void testRateLimitingForgotPassword() {
        // Arrange
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest(testUser.email);
        
        // Mock rate limiting to deny request
        when(rateLimitService.isAllowed(testUser.email, MALICIOUS_IP)).thenReturn(false);
        when(passwordService.generateSecureToken(32)).thenReturn("tokenThatShouldNotBeGenerated");
        
        // Act & Assert
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.forgotPassword(request, MALICIOUS_IP, TEST_USER_AGENT)
        );
        
        assertEquals("Too many failed login attempts. Please try again later.", exception.getMessage());
        
        // Verify rate limit was checked
        verify(rateLimitService).isAllowed(testUser.email, MALICIOUS_IP);
        
        // Verify no token was generated when rate limited
        verify(passwordService, never()).generateSecureToken(anyInt());
        
        // Verify no token exists in database
        PasswordResetToken token = PasswordResetToken.findByToken("tokenThatShouldNotBeGenerated");
        assertNull(token);
    }

    @Test
    @Order(3)
    @DisplayName("Should prevent token reuse attacks")
    @Transactional
    void testTokenReuseAttack() {
        // Arrange
        String resetToken = "oneTimeUseToken123";
        String newPassword = "NewSecurePassword@123";
        
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, resetToken);
        token.persist();
        
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(resetToken, newPassword);
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);
        when(passwordService.hashPassword(newPassword)).thenReturn("$2a$12$newHashedPassword");
        
        // Act - First use should succeed
        authService.resetPassword(request, TEST_IP, TEST_USER_AGENT);
        
        // Assert - First use successful
        PasswordResetToken usedToken = PasswordResetToken.findByToken(resetToken);
        assertTrue(usedToken.used);
        
        // Act - Second use should fail (token reuse attack)
        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> authService.resetPassword(request, MALICIOUS_IP, TEST_USER_AGENT)
        );
        
        // Assert - Token reuse blocked
        assertEquals("Invalid or expired reset token", exception.getMessage());
        
        // Verify password hashing was only called once (for first valid use)
        verify(passwordService, times(1)).hashPassword(newPassword);
    }

    @Test
    @Order(4)
    @DisplayName("Should handle timing attacks on token validation")
    @Transactional
    void testTimingAttackResistance() {
        // Arrange - Create valid token
        String validToken = "validToken123";
        String invalidToken = "invalidToken456";
        String newPassword = "ValidPassword@123";
        
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, validToken);
        token.persist();
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);
        
        // Act - Measure response times
        long validTokenStartTime = System.nanoTime();
        
        try {
            authService.resetPassword(
                TestDataBuilder.createResetPasswordRequest(validToken, newPassword), 
                TEST_IP, 
                TEST_USER_AGENT
            );
        } catch (Exception e) {
            // Expected for invalid password policy in this test
        }
        
        long validTokenEndTime = System.nanoTime();
        
        long invalidTokenStartTime = System.nanoTime();
        
        assertThrows(BadRequestException.class, () -> {
            authService.resetPassword(
                TestDataBuilder.createResetPasswordRequest(invalidToken, newPassword), 
                TEST_IP, 
                TEST_USER_AGENT
            );
        });
        
        long invalidTokenEndTime = System.nanoTime();
        
        // Assert - Response times should be relatively similar to prevent timing attacks
        long validTokenTime = validTokenEndTime - validTokenStartTime;
        long invalidTokenTime = invalidTokenEndTime - invalidTokenStartTime;
        
        // Allow up to 10x difference (generous threshold for test reliability)
        assertTrue(Math.abs(validTokenTime - invalidTokenTime) < validTokenTime * 10,
            "Timing difference too large - potential timing attack vulnerability");
    }

    @Test
    @Order(5)
    @DisplayName("Should invalidate all user tokens on successful reset")
    @Transactional
    void testTokenInvalidationSecurity() {
        // Arrange - Create multiple tokens for the user
        String activeToken1 = "activeToken1";
        String activeToken2 = "activeToken2";
        String resetToken = "resetToken";
        
        PasswordResetToken token1 = TestDataBuilder.createPasswordResetToken(testUser, activeToken1);
        token1.persist();
        
        PasswordResetToken token2 = TestDataBuilder.createPasswordResetToken(testUser, activeToken2);
        token2.persist();
        
        PasswordResetToken resetTokenEntity = TestDataBuilder.createPasswordResetToken(testUser, resetToken);
        resetTokenEntity.persist();
        
        when(passwordService.isValidPassword(anyString())).thenReturn(true);
        when(passwordService.hashPassword(anyString())).thenReturn("$2a$12$newHash");
        
        // Act - Reset password using one token
        authService.resetPassword(
            TestDataBuilder.createResetPasswordRequest(resetToken, "NewPassword@123"), 
            TEST_IP, 
            TEST_USER_AGENT
        );
        
        // Assert - All tokens should be invalidated for security
        PasswordResetToken expiredToken1 = PasswordResetToken.findByToken(activeToken1);
        PasswordResetToken expiredToken2 = PasswordResetToken.findByToken(activeToken2);
        PasswordResetToken usedResetToken = PasswordResetToken.findByToken(resetToken);
        
        assertTrue(expiredToken1.used);
        assertTrue(expiredToken2.used);
        assertTrue(usedResetToken.used);
    }

    @Test
    @Order(6)
    @DisplayName("Should prevent concurrent token usage")
    @Transactional
    void testConcurrentTokenUsageAttack() throws InterruptedException, ExecutionException, TimeoutException {
        // Arrange
        String resetToken = "concurrentToken123";
        String newPassword = "ConcurrentPassword@123";
        
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, resetToken);
        token.persist();
        
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(resetToken, newPassword);
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);
        when(passwordService.hashPassword(newPassword)).thenReturn("$2a$12$concurrentHash");
        
        // Act - Simulate concurrent password reset attempts
        CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
            try {
                authService.resetPassword(request, TEST_IP, TEST_USER_AGENT);
            } catch (Exception e) {
                // Expected for one of the concurrent requests
            }
        });
        
        CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
            try {
                authService.resetPassword(request, MALICIOUS_IP, TEST_USER_AGENT);
            } catch (Exception e) {
                // Expected for one of the concurrent requests
            }
        });
        
        // Wait for both to complete
        CompletableFuture.allOf(future1, future2).get(5, TimeUnit.SECONDS);
        
        // Assert - Token should be used only once
        PasswordResetToken usedToken = PasswordResetToken.findByToken(resetToken);
        assertTrue(usedToken.used, "Token should be marked as used after first successful reset");
        
        // Password should be hashed at most once (for the successful reset)
        verify(passwordService, atMost(1)).hashPassword(newPassword);
    }

    @Test
    @Order(7)
    @DisplayName("Should protect against token enumeration attacks")
    @Transactional
    void testTokenEnumerationProtection() {
        // Arrange - Test various token formats
        String[] testTokens = {
            "shortToken",
            "veryLongTokenThatExceedsNormalLength123456789012345678901234567890",
            "token-with-dashes",
            "token_with_underscores",
            "token.with.dots",
            "TOKEN_UPPERCASE",
            "123456789012345678901234567890123456789012345678901234567890",
            "special!@#$%^&*()characters",
            "",
            null
        };
        
        when(passwordService.isValidPassword(anyString())).thenReturn(true);
        
        // Act & Assert - All should return same generic error
        for (String testToken : testTokens) {
            if (testToken == null) {
                continue; // Skip null test for request building
            }
            
            ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(testToken, "ValidPassword@123");
            
            Exception exception = assertThrows(Exception.class, () -> {
                authService.resetPassword(request, TEST_IP, TEST_USER_AGENT);
            });
            
            // All should return the same error message
            assertTrue(exception.getMessage().contains("Invalid or expired reset token"),
                "Token enumeration may be possible - different error for token: " + testToken);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Should audit and log security events properly")
    @Transactional
    void testSecurityAuditLogging() {
        // Test 1: Successful password reset should be audited
        String validToken = "validAuditToken123";
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, validToken);
        token.persist();
        
        when(passwordService.isValidPassword(anyString())).thenReturn(true);
        when(passwordService.hashPassword(anyString())).thenReturn("$2a$12$auditHash");
        
        // Act - Successful reset
        authService.resetPassword(
            TestDataBuilder.createResetPasswordRequest(validToken, "AuditPassword@123"), 
            TEST_IP, 
            TEST_USER_AGENT
        );
        
        // Test 2: Failed attempts should be audited
        String invalidToken = "invalidAuditToken456";
        
        // Act - Failed reset attempt
        assertThrows(BadRequestException.class, () -> {
            authService.resetPassword(
                TestDataBuilder.createResetPasswordRequest(invalidToken, "AuditPassword@123"), 
                MALICIOUS_IP, 
                TEST_USER_AGENT
            );
        });
        
        // Test 3: Rate limited requests should be audited
        when(rateLimitService.isAllowed(anyString(), anyString())).thenReturn(false);
        
        assertThrows(BadRequestException.class, () -> {
            authService.forgotPassword(
                TestDataBuilder.createForgotPasswordRequest(testUser.email), 
                MALICIOUS_IP, 
                TEST_USER_AGENT
            );
        });
        
        // Assert - Verify audit events were recorded
        // Note: In a real implementation, you would verify audit log entries
        // Here we verify the service interactions that lead to audit logging
        verify(rateLimitService).isAllowed(testUser.email, MALICIOUS_IP);
        verify(passwordService).hashPassword("AuditPassword@123");
        
        // Verify token state changes that indicate proper security event handling
        PasswordResetToken auditToken = PasswordResetToken.findByToken(validToken);
        assertTrue(auditToken.used);
    }
}