package br.com.vortex.authorization.resource;

import br.com.vortex.authorization.dto.*;
import br.com.vortex.authorization.entity.*;
import br.com.vortex.authorization.util.TestDataBuilder;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestHTTPEndpoint(AuthResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthResourceIntegrationTest {
    
    @Inject
    EntityManager entityManager;
    
    private String testEmail;
    private String testUsername;
    private String testPassword = "Test@Password123";
    private String accessToken;
    private String refreshToken;
    
    @BeforeAll
    void setUpAll() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
    
    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up test data before each test
        entityManager.createQuery("DELETE FROM RefreshToken").executeUpdate();
        entityManager.createQuery("DELETE FROM PasswordResetToken").executeUpdate();
        entityManager.createQuery("DELETE FROM LoginAttempt").executeUpdate();
        entityManager.createQuery("DELETE FROM AuditLog").executeUpdate();
        entityManager.createQuery("DELETE FROM Credential").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM auth.user_roles").executeUpdate();
        entityManager.createQuery("DELETE FROM User").executeUpdate();
        entityManager.createQuery("DELETE FROM Role").executeUpdate();
        
        // Create default roles
        Role userRole = TestDataBuilder.createUserRole();
        userRole.persist();
        
        Role adminRole = TestDataBuilder.createAdminRole();
        adminRole.persist();
        
        entityManager.flush();
    }
    
    @Test
    @Order(1)
    @DisplayName("POST /register - Should successfully register a new user")
    void testRegisterSuccess() {
        RegisterRequest request = TestDataBuilder.createValidRegisterRequest();
        testEmail = request.email;
        testUsername = request.username;
        
        Response response = given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(request)
            .when()
            .post("/register")
            .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .body("success", is(true))
            .body("message", is("Registration successful"))
            .body("data.accessToken", notNullValue())
            .body("data.refreshToken", notNullValue())
            .body("data.expiresIn", greaterThan(0))
            .body("data.user.email", is(request.email))
            .body("data.user.username", is(request.username))
            .body("data.user.isActive", is(true))
            .body("data.user.isVerified", is(true))
            .body("data.user.roles", hasItem("USER"))
            .extract()
            .response();
        
        // Store tokens for later tests
        Map<String, Object> data = response.jsonPath().getMap("data");
        accessToken = (String) data.get("accessToken");
        refreshToken = (String) data.get("refreshToken");
        
        assertNotNull(accessToken);
        assertNotNull(refreshToken);
    }
    
    @Test
    @Order(2)
    @DisplayName("POST /register - Should fail with duplicate email")
    @Transactional
    void testRegisterDuplicateEmail() {
        // Create existing user
        User existingUser = TestDataBuilder.createTestUser();
        existingUser.persist();
        
        RegisterRequest request = TestDataBuilder.createValidRegisterRequest();
        request.email = existingUser.email;
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(request)
            .when()
            .post("/register")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("success", is(false))
            .body("message", is("Email already registered"));
    }
    
    @Test
    @Order(3)
    @DisplayName("POST /register - Should fail with duplicate username")
    @Transactional
    void testRegisterDuplicateUsername() {
        // Create existing user
        User existingUser = TestDataBuilder.createTestUser();
        existingUser.persist();
        
        RegisterRequest request = TestDataBuilder.createValidRegisterRequest();
        request.username = existingUser.username;
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(request)
            .when()
            .post("/register")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("success", is(false))
            .body("message", is("Username already taken"));
    }
    
    @Test
    @Order(4)
    @DisplayName("POST /register - Should fail with invalid email format")
    void testRegisterInvalidEmail() {
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithInvalidEmail();
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(request)
            .when()
            .post("/register")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON);
    }
    
    @Test
    @Order(5)
    @DisplayName("POST /register - Should fail with short username")
    void testRegisterShortUsername() {
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithShortUsername();
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(request)
            .when()
            .post("/register")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON);
    }
    
    @Test
    @Order(6)
    @DisplayName("POST /register - Should fail with invalid username characters")
    void testRegisterInvalidUsernameCharacters() {
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithInvalidUsername();
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(request)
            .when()
            .post("/register")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON);
    }
    
    @Test
    @Order(7)
    @DisplayName("POST /register - Should fail when passwords don't match")
    void testRegisterPasswordMismatch() {
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithMismatchedPasswords();
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(request)
            .when()
            .post("/register")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("success", is(false))
            .body("message", containsString("Passwords do not match"));
    }
    
    @Test
    @Order(8)
    @DisplayName("POST /register - Should fail with weak password")
    void testRegisterWeakPassword() {
        RegisterRequest request = TestDataBuilder.createRegisterRequestWithWeakPassword();
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(request)
            .when()
            .post("/register")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON);
    }
    
    @Test
    @Order(9)
    @DisplayName("POST /login - Should successfully login with email")
    @Transactional
    void testLoginWithEmailSuccess() {
        // Create user first
        RegisterRequest registerRequest = TestDataBuilder.createValidRegisterRequest();
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(registerRequest)
            .when()
            .post("/register")
            .then()
            .statusCode(201);
        
        // Now login
        LoginRequest loginRequest = TestDataBuilder.createLoginRequest(
            registerRequest.email, 
            registerRequest.password
        );
        
        Response response = given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(loginRequest)
            .when()
            .post("/login")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", is(true))
            .body("message", is("Login successful"))
            .body("data.accessToken", notNullValue())
            .body("data.refreshToken", notNullValue())
            .body("data.user.email", is(registerRequest.email))
            .extract()
            .response();
        
        String loginAccessToken = response.jsonPath().getString("data.accessToken");
        String loginRefreshToken = response.jsonPath().getString("data.refreshToken");
        
        assertNotNull(loginAccessToken);
        assertNotNull(loginRefreshToken);
    }
    
    @Test
    @Order(10)
    @DisplayName("POST /login - Should successfully login with username")
    @Transactional
    void testLoginWithUsernameSuccess() {
        // Create user first
        RegisterRequest registerRequest = TestDataBuilder.createValidRegisterRequest();
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(registerRequest)
            .when()
            .post("/register")
            .then()
            .statusCode(201);
        
        // Login with username
        LoginRequest loginRequest = TestDataBuilder.createLoginRequest(
            registerRequest.username, 
            registerRequest.password
        );
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(loginRequest)
            .when()
            .post("/login")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", is(true))
            .body("data.user.username", is(registerRequest.username));
    }
    
    @Test
    @Order(11)
    @DisplayName("POST /login - Should fail with invalid password")
    @Transactional
    void testLoginInvalidPassword() {
        // Create user first
        RegisterRequest registerRequest = TestDataBuilder.createValidRegisterRequest();
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(registerRequest)
            .when()
            .post("/register")
            .then()
            .statusCode(201);
        
        // Try to login with wrong password
        LoginRequest loginRequest = TestDataBuilder.createLoginRequest(
            registerRequest.email, 
            "WrongPassword123!"
        );
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(loginRequest)
            .when()
            .post("/login")
            .then()
            .statusCode(401)
            .contentType(ContentType.JSON)
            .body("success", is(false))
            .body("message", is("Invalid credentials"));
    }
    
    @Test
    @Order(12)
    @DisplayName("POST /login - Should fail with non-existent user")
    void testLoginNonExistentUser() {
        LoginRequest loginRequest = TestDataBuilder.createLoginRequest(
            "nonexistent@email.com", 
            "Password123!"
        );
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(loginRequest)
            .when()
            .post("/login")
            .then()
            .statusCode(401)
            .contentType(ContentType.JSON)
            .body("success", is(false))
            .body("message", is("Invalid credentials"));
    }
    
    @Test
    @Order(13)
    @DisplayName("POST /refresh - Should successfully refresh access token")
    @Transactional
    void testRefreshTokenSuccess() {
        // Create and login user
        RegisterRequest registerRequest = TestDataBuilder.createValidRegisterRequest();
        
        Response registerResponse = given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(registerRequest)
            .when()
            .post("/register")
            .then()
            .statusCode(201)
            .extract()
            .response();
        
        String refreshToken = registerResponse.jsonPath().getString("data.refreshToken");
        
        // Refresh token
        RefreshTokenRequest refreshRequest = TestDataBuilder.createRefreshTokenRequest(refreshToken);
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(refreshRequest)
            .when()
            .post("/refresh")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", is(true))
            .body("message", is("Token refreshed successfully"))
            .body("data.accessToken", notNullValue())
            .body("data.refreshToken", is(refreshToken))
            .body("data.user.email", is(registerRequest.email));
    }
    
    @Test
    @Order(14)
    @DisplayName("POST /refresh - Should fail with invalid refresh token")
    void testRefreshTokenInvalid() {
        RefreshTokenRequest refreshRequest = TestDataBuilder.createRefreshTokenRequest("invalid.refresh.token");
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(refreshRequest)
            .when()
            .post("/refresh")
            .then()
            .statusCode(401)
            .contentType(ContentType.JSON)
            .body("success", is(false))
            .body("message", is("Invalid or expired refresh token"));
    }
    
    @Test
    @Order(15)
    @DisplayName("POST /logout - Should successfully logout user")
    @Transactional
    void testLogoutSuccess() {
        // Create and login user
        RegisterRequest registerRequest = TestDataBuilder.createValidRegisterRequest();
        
        Response registerResponse = given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(registerRequest)
            .when()
            .post("/register")
            .then()
            .statusCode(201)
            .extract()
            .response();
        
        String refreshToken = registerResponse.jsonPath().getString("data.refreshToken");
        
        // Logout
        RefreshTokenRequest logoutRequest = TestDataBuilder.createRefreshTokenRequest(refreshToken);
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(logoutRequest)
            .when()
            .post("/logout")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", is(true))
            .body("message", is("Logout successful"));
        
        // Try to use the refresh token after logout - should fail
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(logoutRequest)
            .when()
            .post("/refresh")
            .then()
            .statusCode(401);
    }
    
    @Test
    @Order(16)
    @DisplayName("POST /forgot-password - Should accept valid email")
    @Transactional
    void testForgotPasswordSuccess() {
        // Create user
        RegisterRequest registerRequest = TestDataBuilder.createValidRegisterRequest();
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(registerRequest)
            .when()
            .post("/register")
            .then()
            .statusCode(201);
        
        // Request password reset
        ForgotPasswordRequest forgotRequest = TestDataBuilder.createForgotPasswordRequest(registerRequest.email);
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(forgotRequest)
            .when()
            .post("/forgot-password")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", is(true))
            .body("message", is("If the email exists, a password reset link has been sent"));
    }
    
    @Test
    @Order(17)
    @DisplayName("POST /forgot-password - Should silently handle non-existent email")
    void testForgotPasswordNonExistentEmail() {
        ForgotPasswordRequest forgotRequest = TestDataBuilder.createForgotPasswordRequest("nonexistent@email.com");
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(forgotRequest)
            .when()
            .post("/forgot-password")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("success", is(true))
            .body("message", is("If the email exists, a password reset link has been sent"));
    }
    
    @Test
    @Order(18)
    @DisplayName("POST /validate-token - Should validate valid JWT token")
    @Transactional
    void testValidateTokenSuccess() {
        // Create and login user to get a valid token
        RegisterRequest registerRequest = TestDataBuilder.createValidRegisterRequest();
        
        Response registerResponse = given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(registerRequest)
            .when()
            .post("/register")
            .then()
            .statusCode(201)
            .extract()
            .response();
        
        String accessToken = registerResponse.jsonPath().getString("data.accessToken");
        
        // Validate the token
        ValidateTokenRequest validateRequest = TestDataBuilder.createValidateTokenRequest(accessToken);
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(validateRequest)
            .when()
            .post("/validate-token")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("isValid", is(true))
            .body("username", is(registerRequest.username))
            .body("email", is(registerRequest.email))
            .body("roles", hasItem("USER"));
    }
    
    @Test
    @Order(19)
    @DisplayName("POST /validate-token - Should return invalid for malformed token")
    void testValidateTokenInvalid() {
        ValidateTokenRequest validateRequest = TestDataBuilder.createValidateTokenRequest("invalid.jwt.token");
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Integration Test")
            .body(validateRequest)
            .when()
            .post("/validate-token")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("isValid", is(false))
            .body("username", nullValue())
            .body("email", nullValue())
            .body("roles", nullValue());
    }
    
    @Test
    @Order(20)
    @DisplayName("Registration flow - Should handle complete registration with proper headers")
    void testCompleteRegistrationFlowWithHeaders() {
        RegisterRequest request = TestDataBuilder.createValidRegisterRequest();
        
        given()
            .contentType(ContentType.JSON)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("X-Forwarded-For", "192.168.1.100")
            .header("X-Real-IP", "192.168.1.100")
            .body(request)
            .when()
            .post("/register")
            .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .body("success", is(true))
            .body("data.accessToken", notNullValue())
            .body("data.refreshToken", notNullValue())
            .body("data.user.email", is(request.email))
            .body("data.user.username", is(request.username))
            .body("data.user.roles", hasItem("USER"));
    }
}