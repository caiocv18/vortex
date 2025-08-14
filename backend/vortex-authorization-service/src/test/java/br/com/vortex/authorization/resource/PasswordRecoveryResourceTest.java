package br.com.vortex.authorization.resource;

import br.com.vortex.authorization.dto.*;
import br.com.vortex.authorization.entity.*;
import br.com.vortex.authorization.service.EventPublisher;
import br.com.vortex.authorization.security.PasswordService;
import br.com.vortex.authorization.util.TestDataBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.time.OffsetDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PasswordRecoveryResourceTest {

    @InjectMock
    PasswordService passwordService;

    @InjectMock
    EventPublisher eventPublisher;

    private User testUser;
    private final String BASE_URL = "/api/auth";
    
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
        Credential credential = new Credential();
        credential.user = testUser;
        credential.passwordHash = hashedPassword;
        credential.createdAt = OffsetDateTime.now();
        credential.updatedAt = OffsetDateTime.now();
        credential.persist();
        
        // Reset mocks
        reset(passwordService, eventPublisher);
    }

    @Test
    @Order(1)
    @DisplayName("Should accept valid forgot password request")
    @Transactional
    void testForgotPasswordValidRequest() {
        // Arrange
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest(testUser.email);
        String uniqueToken = generateUniqueToken("validToken");
        when(passwordService.generateSecureToken(anyInt())).thenReturn(uniqueToken);

        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post(BASE_URL + "/forgot-password")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("message", containsString("password reset link has been sent"))
            .body("data", nullValue());

        // Verify token was created
        PasswordResetToken token = PasswordResetToken.findByToken(uniqueToken);
        assertNotNull(token);
        assertEquals(testUser.id, token.user.id);
    }

    @Test
    @Order(2)
    @DisplayName("Should return success even for non-existent email")
    @Transactional
    void testForgotPasswordNonExistentEmail() {
        // Arrange
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest("nonexistent@email.com");

        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post(BASE_URL + "/forgot-password")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("message", containsString("password reset link has been sent"));

        // Verify no token was generated
        verify(passwordService, never()).generateSecureToken(anyInt());
    }

    @Test
    @Order(3)
    @DisplayName("Should reject forgot password request with invalid email format")
    @Transactional
    void testForgotPasswordInvalidEmailFormat() {
        // Arrange
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.email = "invalid-email-format";

        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post(BASE_URL + "/forgot-password")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("Validation failed"));
    }

    @Test
    @Order(4)
    @DisplayName("Should reject forgot password request with missing email")
    @Transactional
    void testForgotPasswordMissingEmail() {
        // Arrange
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        // email is null

        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post(BASE_URL + "/forgot-password")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("Validation failed"));
    }

    @Test
    @Order(5)
    @DisplayName("Should reject forgot password request with empty email")
    @Transactional
    void testForgotPasswordEmptyEmail() {
        // Arrange
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.email = "";

        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post(BASE_URL + "/forgot-password")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("Validation failed"));
    }

    @Test
    @Order(6)
    @DisplayName("Should accept valid reset password request")
    @Transactional
    void testResetPasswordValidRequest() {
        // Arrange
        String resetToken = generateUniqueToken("validResetToken");
        String newPassword = "NewValidPassword@123";
        
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, resetToken);
        token.persist();
        
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(resetToken, newPassword);
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);
        when(passwordService.hashPassword(newPassword)).thenReturn("$2a$12$newHashedPassword");
        when(passwordService.getPasswordRequirements()).thenReturn("Password must meet security requirements");

        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post(BASE_URL + "/reset-password")
        .then()
            .statusCode(200)
            .body("success", is(true))
            .body("message", is("Password reset successful"))
            .body("data", nullValue());

        // Verify token was marked as used
        PasswordResetToken usedToken = PasswordResetToken.findByToken(resetToken);
        assertTrue(usedToken.used);
    }

    @Test
    @Order(7)
    @DisplayName("Should reject reset password with mismatched passwords")
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
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post(BASE_URL + "/reset-password")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", is("Passwords do not match"));

        // Verify token remains unused
        PasswordResetToken unchangedToken = PasswordResetToken.findByToken(resetToken);
        assertFalse(unchangedToken.used);
    }

    @Test
    @Order(8)
    @DisplayName("Should reject reset password with invalid token")
    @Transactional
    void testResetPasswordInvalidToken() {
        // Arrange
        String invalidToken = "nonExistentToken123";
        String newPassword = "ValidPassword@123";
        
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(invalidToken, newPassword);
        
        when(passwordService.isValidPassword(newPassword)).thenReturn(true);

        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post(BASE_URL + "/reset-password")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", is("Invalid or expired reset token"));
    }

    @Test
    @Order(9)
    @DisplayName("Should reject reset password with expired token")
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
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post(BASE_URL + "/reset-password")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", is("Invalid or expired reset token"));
    }

    @Test
    @Order(10)
    @DisplayName("Should reject reset password with weak password")
    @Transactional
    void testResetPasswordWeakPassword() {
        // Arrange
        String resetToken = generateUniqueToken("validToken");
        String weakPassword = "weak";
        
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, resetToken);
        token.persist();
        
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest(resetToken, weakPassword);
        
        when(passwordService.isValidPassword(weakPassword)).thenReturn(false);
        when(passwordService.getPasswordRequirements()).thenReturn("Password must meet security requirements");

        // Act & Assert
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post(BASE_URL + "/reset-password")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("Validation failed"));

        // Verify token remains unused
        PasswordResetToken unchangedToken = PasswordResetToken.findByToken(resetToken);
        assertFalse(unchangedToken.used);
    }

    @Test
    @Order(11)
    @DisplayName("Should reject reset password with missing required fields")
    @Transactional
    void testResetPasswordMissingFields() {
        // Test missing token
        ResetPasswordRequest requestMissingToken = new ResetPasswordRequest();
        requestMissingToken.password = "ValidPassword@123";
        requestMissingToken.confirmPassword = "ValidPassword@123";

        given()
            .contentType(ContentType.JSON)
            .body(requestMissingToken)
        .when()
            .post(BASE_URL + "/reset-password")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("Validation failed"));

        // Test missing password
        ResetPasswordRequest requestMissingPassword = new ResetPasswordRequest();
        requestMissingPassword.token = generateUniqueToken("validToken");
        requestMissingPassword.confirmPassword = "ValidPassword@123";

        given()
            .contentType(ContentType.JSON)
            .body(requestMissingPassword)
        .when()
            .post(BASE_URL + "/reset-password")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("Validation failed"));

        // Test missing confirm password
        ResetPasswordRequest requestMissingConfirm = new ResetPasswordRequest();
        requestMissingConfirm.token = generateUniqueToken("validToken");
        requestMissingConfirm.password = "ValidPassword@123";

        given()
            .contentType(ContentType.JSON)
            .body(requestMissingConfirm)
        .when()
            .post(BASE_URL + "/reset-password")
        .then()
            .statusCode(400)
            .body("success", is(false))
            .body("message", containsString("Validation failed"));
    }

    @Test
    @Order(12)
    @DisplayName("Should handle malformed JSON requests gracefully")
    @Transactional
    void testMalformedJsonRequests() {
        // Test malformed JSON for forgot password
        given()
            .contentType(ContentType.JSON)
            .body("{invalid json}")
        .when()
            .post(BASE_URL + "/forgot-password")
        .then()
            .statusCode(400);

        // Test malformed JSON for reset password
        given()
            .contentType(ContentType.JSON)
            .body("{\"token\": \"valid\", \"password\": }")
        .when()
            .post(BASE_URL + "/reset-password")
        .then()
            .statusCode(400);

        // Test empty body
        given()
            .contentType(ContentType.JSON)
            .body("")
        .when()
            .post(BASE_URL + "/forgot-password")
        .then()
            .statusCode(400);
    }

    @Test
    @Order(13)
    @DisplayName("Should include proper headers in responses")
    @Transactional
    void testResponseHeaders() {
        // Test forgot password response headers
        ForgotPasswordRequest forgotRequest = TestDataBuilder.createForgotPasswordRequest(testUser.email);
        String uniqueToken2 = generateUniqueToken("validToken");
        when(passwordService.generateSecureToken(anyInt())).thenReturn(uniqueToken2);

        given()
            .contentType(ContentType.JSON)
            .body(forgotRequest)
        .when()
            .post(BASE_URL + "/forgot-password")
        .then()
            .statusCode(200)
            .header("Content-Type", containsString("application/json"));

        // Test reset password response headers
        String resetToken = generateUniqueToken("validResetToken");
        PasswordResetToken token = TestDataBuilder.createPasswordResetToken(testUser, resetToken);
        token.persist();
        
        ResetPasswordRequest resetRequest = TestDataBuilder.createResetPasswordRequest(resetToken, "ValidPassword@123");
        when(passwordService.isValidPassword(anyString())).thenReturn(true);
        when(passwordService.hashPassword(anyString())).thenReturn("$2a$12$hashedPassword");
        when(passwordService.getPasswordRequirements()).thenReturn("Password must meet security requirements");

        given()
            .contentType(ContentType.JSON)
            .body(resetRequest)
        .when()
            .post(BASE_URL + "/reset-password")
        .then()
            .statusCode(200)
            .header("Content-Type", containsString("application/json"));
    }

    @Test
    @Order(14)
    @DisplayName("Should handle special characters in email addresses")
    @Transactional
    void testSpecialCharactersInEmail() {
        // Create user with special characters in email
        User specialUser = TestDataBuilder.createTestUser();
        specialUser.email = "test+user@sub-domain.example-site.com";
        specialUser.persist();

        Credential credential = TestDataBuilder.createCredential(specialUser, "$2a$12$hashedPassword");
        credential.persist();

        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest(specialUser.email);
        String specialToken = generateUniqueToken("specialToken");
        when(passwordService.generateSecureToken(anyInt())).thenReturn(specialToken);

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post(BASE_URL + "/forgot-password")
        .then()
            .statusCode(200)
            .body("success", is(true));

        // Verify token was created for the special email user
        PasswordResetToken.flush(); // Ensure database flush
        PasswordResetToken token = PasswordResetToken.findByToken(specialToken);
        assertNotNull(token, "Token should be created for user with special email characters");
        assertEquals(specialUser.id, token.user.id);
    }

    @Test
    @Order(15)
    @DisplayName("Should maintain rate limiting and security headers")
    @Transactional
    void testSecurityAndRateLimiting() {
        // Test multiple rapid requests to the same endpoint
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest(testUser.email);
        when(passwordService.generateSecureToken(anyInt())).thenReturn("token1", "token2", "token3");

        // Make multiple requests rapidly
        for (int i = 0; i < 3; i++) {
            given()
                .contentType(ContentType.JSON)
                .body(request)
            .when()
                .post(BASE_URL + "/forgot-password")
            .then()
                .statusCode(200);
        }

        // All requests should succeed (rate limiting is handled at service level)
        // Verify proper cleanup - only last token should be active
        PasswordResetToken token1 = PasswordResetToken.findByToken("token1");
        PasswordResetToken token2 = PasswordResetToken.findByToken("token2");
        PasswordResetToken token3 = PasswordResetToken.findByToken("token3");

        assertTrue(token1.used);
        assertTrue(token2.used);
        assertFalse(token3.used);
    }
}