package br.com.vortex.authorization.dto;

import br.com.vortex.authorization.util.TestDataBuilder;
import org.junit.jupiter.api.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DTO validation without Quarkus context
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ValidationTest {
    
    private static Validator validator;
    
    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    @Test
    @Order(1)
    @DisplayName("Valid RegisterRequest should pass validation")
    void testValidRegisterRequest() {
        RegisterRequest request = TestDataBuilder.createValidRegisterRequest();
        
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        
        assertTrue(violations.isEmpty(), 
            "Valid request should not have violations: " + violations.toString());
    }
    
    @Test
    @Order(2)
    @DisplayName("RegisterRequest with invalid email should fail validation")
    void testInvalidEmailValidation() {
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithInvalidEmail();
        
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("email")),
            "Should have email validation error");
    }
    
    @Test
    @Order(3)
    @DisplayName("RegisterRequest with short username should fail validation")
    void testShortUsernameValidation() {
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithShortUsername();
        
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("username")),
            "Should have username validation error");
    }
    
    @Test
    @Order(4)
    @DisplayName("RegisterRequest with invalid username characters should fail validation")
    void testInvalidUsernameCharactersValidation() {
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithInvalidUsername();
        
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("username")),
            "Should have username validation error");
    }
    
    @Test
    @Order(5)
    @DisplayName("RegisterRequest with empty fields should fail validation")
    void testEmptyFieldsValidation() {
        RegisterRequest request = new RegisterRequest();
        
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.size() >= 3, "Should have at least 3 violations for required fields");
        
        // Check for specific field violations
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("email")));
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("username")));
        assertTrue(violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }
    
    @Test
    @Order(6)
    @DisplayName("LoginRequest validation should work correctly")
    void testLoginRequestValidation() {
        // Valid login request
        LoginRequest validRequest = TestDataBuilder.createLoginRequest("user@test.com", "password");
        Set<ConstraintViolation<LoginRequest>> validViolations = validator.validate(validRequest);
        assertTrue(validViolations.isEmpty(), "Valid login request should pass validation");
        
        // Invalid login request
        LoginRequest invalidRequest = new LoginRequest();
        Set<ConstraintViolation<LoginRequest>> invalidViolations = validator.validate(invalidRequest);
        assertFalse(invalidViolations.isEmpty(), "Empty login request should fail validation");
    }
    
    @Test
    @Order(7)
    @DisplayName("ForgotPasswordRequest validation should work correctly")
    void testForgotPasswordRequestValidation() {
        // Valid request
        ForgotPasswordRequest validRequest = TestDataBuilder.createForgotPasswordRequest("user@test.com");
        Set<ConstraintViolation<ForgotPasswordRequest>> validViolations = validator.validate(validRequest);
        assertTrue(validViolations.isEmpty(), "Valid forgot password request should pass validation");
        
        // Invalid request
        ForgotPasswordRequest invalidRequest = new ForgotPasswordRequest();
        invalidRequest.email = "invalid-email";
        Set<ConstraintViolation<ForgotPasswordRequest>> invalidViolations = validator.validate(invalidRequest);
        assertFalse(invalidViolations.isEmpty(), "Invalid email should fail validation");
    }
    
    @Test
    @Order(8)
    @DisplayName("ResetPasswordRequest validation should work correctly")
    void testResetPasswordRequestValidation() {
        // Valid request
        ResetPasswordRequest validRequest = TestDataBuilder.createResetPasswordRequest("token123", "NewPassword@123");
        Set<ConstraintViolation<ResetPasswordRequest>> validViolations = validator.validate(validRequest);
        assertTrue(validViolations.isEmpty(), "Valid reset password request should pass validation");
        
        // Invalid request
        ResetPasswordRequest invalidRequest = new ResetPasswordRequest();
        Set<ConstraintViolation<ResetPasswordRequest>> invalidViolations = validator.validate(invalidRequest);
        assertFalse(invalidViolations.isEmpty(), "Empty reset password request should fail validation");
    }
    
    @Test
    @Order(9)
    @DisplayName("RefreshTokenRequest validation should work correctly")
    void testRefreshTokenRequestValidation() {
        // Valid request
        RefreshTokenRequest validRequest = TestDataBuilder.createRefreshTokenRequest("refresh.token.123");
        Set<ConstraintViolation<RefreshTokenRequest>> validViolations = validator.validate(validRequest);
        assertTrue(validViolations.isEmpty(), "Valid refresh token request should pass validation");
        
        // Invalid request
        RefreshTokenRequest invalidRequest = new RefreshTokenRequest();
        Set<ConstraintViolation<RefreshTokenRequest>> invalidViolations = validator.validate(invalidRequest);
        assertFalse(invalidViolations.isEmpty(), "Empty refresh token request should fail validation");
    }
    
    @Test
    @Order(10)
    @DisplayName("Validation messages should be meaningful")
    void testValidationMessages() {
        RegisterRequest request = new RegisterRequest();
        request.email = "invalid";
        request.username = "ab";
        request.password = "";
        request.confirmPassword = "";
        
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        
        assertFalse(violations.isEmpty());
        
        // Check that validation messages are meaningful
        violations.forEach(violation -> {
            assertNotNull(violation.getMessage());
            assertFalse(violation.getMessage().trim().isEmpty());
        });
    }
}