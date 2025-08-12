package br.com.vortex.authorization.resource;

import br.com.vortex.authorization.dto.*;
import br.com.vortex.authorization.util.TestDataBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Simplified integration tests for AuthResource
 * Tests only basic REST endpoint validation without complex business logic
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimpleAuthResourceTest {
    
    @Test
    @Order(1)
    @DisplayName("POST /api/auth/register - Should validate required fields")
    void testRegisterRequiredFields() {
        RegisterRequest emptyRequest = new RegisterRequest();
        
        given()
            .contentType(ContentType.JSON)
            .body(emptyRequest)
            .when()
            .post("/api/auth/register")
            .then()
            .statusCode(400); // Should return 400 for validation errors
    }
    
    @Test
    @Order(2)
    @DisplayName("POST /api/auth/register - Should validate email format")
    void testRegisterInvalidEmail() {
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithInvalidEmail();
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/auth/register")
            .then()
            .statusCode(400); // Should return 400 for validation errors
    }
    
    @Test
    @Order(3)
    @DisplayName("POST /api/auth/register - Should validate username length")
    void testRegisterShortUsername() {
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithShortUsername();
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/auth/register")
            .then()
            .statusCode(400); // Should return 400 for validation errors
    }
    
    @Test
    @Order(4)
    @DisplayName("POST /api/auth/login - Should validate required fields")
    void testLoginRequiredFields() {
        LoginRequest emptyRequest = new LoginRequest();
        
        given()
            .contentType(ContentType.JSON)
            .body(emptyRequest)
            .when()
            .post("/api/auth/login")
            .then()
            .statusCode(400); // Should return 400 for validation errors
    }
    
    @Test
    @Order(5)
    @DisplayName("POST /api/auth/login - Should fail with invalid credentials")
    void testLoginInvalidCredentials() {
        LoginRequest request = TestDataBuilder.createLoginRequest("nonexistent@email.com", "wrongpassword");
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/auth/login")
            .then()
            .statusCode(401); // Should return 401 for invalid credentials
    }
    
    @Test
    @Order(6)
    @DisplayName("POST /api/auth/refresh - Should validate required fields")
    void testRefreshRequiredFields() {
        RefreshTokenRequest emptyRequest = new RefreshTokenRequest();
        
        given()
            .contentType(ContentType.JSON)
            .body(emptyRequest)
            .when()
            .post("/api/auth/refresh")
            .then()
            .statusCode(400); // Should return 400 for validation errors
    }
    
    @Test
    @Order(7)
    @DisplayName("POST /api/auth/refresh - Should fail with invalid token")
    void testRefreshInvalidToken() {
        RefreshTokenRequest request = TestDataBuilder.createRefreshTokenRequest("invalid.token");
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/auth/refresh")
            .then()
            .statusCode(401); // Should return 401 for invalid token
    }
    
    @Test
    @Order(8)
    @DisplayName("POST /api/auth/logout - Should accept valid structure")
    void testLogoutStructure() {
        RefreshTokenRequest request = TestDataBuilder.createRefreshTokenRequest("some.token.value");
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/auth/logout")
            .then()
            .statusCode(200); // Should return 200 even if token is invalid (logout is idempotent)
    }
    
    @Test
    @Order(9)
    @DisplayName("POST /api/auth/forgot-password - Should validate email format")
    void testForgotPasswordValidation() {
        ForgotPasswordRequest invalidRequest = new ForgotPasswordRequest();
        invalidRequest.email = "invalid-email";
        
        given()
            .contentType(ContentType.JSON)
            .body(invalidRequest)
            .when()
            .post("/api/auth/forgot-password")
            .then()
            .statusCode(400); // Should return 400 for validation errors
    }
    
    @Test
    @Order(10)
    @DisplayName("POST /api/auth/forgot-password - Should accept valid email")
    void testForgotPasswordValidEmail() {
        ForgotPasswordRequest request = TestDataBuilder.createForgotPasswordRequest("test@example.com");
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/auth/forgot-password")
            .then()
            .statusCode(200) // Should return 200 regardless of whether email exists (security)
            .body("success", is(true))
            .body("message", containsString("password reset"));
    }
    
    @Test
    @Order(11)
    @DisplayName("POST /api/auth/reset-password - Should validate required fields")
    void testResetPasswordRequiredFields() {
        ResetPasswordRequest emptyRequest = new ResetPasswordRequest();
        
        given()
            .contentType(ContentType.JSON)
            .body(emptyRequest)
            .when()
            .post("/api/auth/reset-password")
            .then()
            .statusCode(400); // Should return 400 for validation errors
    }
    
    @Test
    @Order(12)
    @DisplayName("POST /api/auth/reset-password - Should fail with invalid token")
    void testResetPasswordInvalidToken() {
        ResetPasswordRequest request = TestDataBuilder.createResetPasswordRequest("invalid-token", "NewPass@123");
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/auth/reset-password")
            .then()
            .statusCode(400); // Should return 400 for invalid token
    }
    
    @Test
    @Order(13)
    @DisplayName("POST /api/auth/validate-token - Should handle invalid token")
    void testValidateTokenInvalid() {
        ValidateTokenRequest request = TestDataBuilder.createValidateTokenRequest("invalid.jwt.token");
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/auth/validate-token")
            .then()
            .statusCode(200) // Should return 200 with valid=false
            .body("valid", is(false));
    }
    
    @Test
    @Order(14)
    @DisplayName("All endpoints should return JSON content type")
    void testContentTypeReturned() {
        // Test that all endpoints return JSON
        given()
            .contentType(ContentType.JSON)
            .body(TestDataBuilder.createForgotPasswordRequest("test@example.com"))
            .when()
            .post("/api/auth/forgot-password")
            .then()
            .contentType(ContentType.JSON);
            
        given()
            .contentType(ContentType.JSON)
            .body(TestDataBuilder.createValidateTokenRequest("invalid.token"))
            .when()
            .post("/api/auth/validate-token")
            .then()
            .contentType(ContentType.JSON);
    }
    
    @Test
    @Order(15)
    @DisplayName("All endpoints should handle missing Content-Type")
    void testMissingContentType() {
        RegisterRequest request = TestDataBuilder.createValidRegisterRequest();
        
        given()
            .body(request)
            // No Content-Type header
            .when()
            .post("/api/auth/register")
            .then()
            .statusCode(anyOf(is(400), is(415))); // Should return 400 or 415 for unsupported media type
    }
}