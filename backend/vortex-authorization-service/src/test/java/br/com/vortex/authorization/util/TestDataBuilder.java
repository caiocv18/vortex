package br.com.vortex.authorization.util;

import br.com.vortex.authorization.dto.*;
import br.com.vortex.authorization.entity.*;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public class TestDataBuilder {
    
    private static int userCounter = 0;
    
    public static RegisterRequest createValidRegisterRequest() {
        userCounter++;
        RegisterRequest request = new RegisterRequest();
        request.email = "testuser" + userCounter + "@test.com";
        request.username = "testuser" + userCounter;
        request.password = "Test@Password123";
        request.confirmPassword = "Test@Password123";
        return request;
    }
    
    public static RegisterRequest createRegisterRequestWithWeakPassword() {
        RegisterRequest request = createValidRegisterRequest();
        request.password = "weak";
        request.confirmPassword = "weak";
        return request;
    }
    
    public static RegisterRequest createRegisterRequestWithMismatchedPasswords() {
        RegisterRequest request = createValidRegisterRequest();
        request.confirmPassword = "Different@Password123";
        return request;
    }
    
    public static RegisterRequest createRegisterRequestWithInvalidEmail() {
        RegisterRequest request = createValidRegisterRequest();
        request.email = "invalid-email";
        return request;
    }
    
    public static RegisterRequest createRegisterRequestWithShortUsername() {
        RegisterRequest request = createValidRegisterRequest();
        request.username = "ab";
        return request;
    }
    
    public static RegisterRequest createRegisterRequestWithInvalidUsername() {
        RegisterRequest request = createValidRegisterRequest();
        request.username = "test user with spaces";
        return request;
    }
    
    public static LoginRequest createLoginRequest(String identifier, String password) {
        LoginRequest request = new LoginRequest();
        request.identifier = identifier;
        request.password = password;
        return request;
    }
    
    public static User createTestUser() {
        userCounter++;
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = "testuser" + userCounter + "@test.com";
        user.username = "testuser" + userCounter;
        user.isActive = true;
        user.isVerified = true;
        user.createdAt = OffsetDateTime.now();
        user.updatedAt = OffsetDateTime.now();
        return user;
    }
    
    public static User createInactiveUser() {
        User user = createTestUser();
        user.isActive = false;
        return user;
    }
    
    public static User createUnverifiedUser() {
        User user = createTestUser();
        user.isVerified = false;
        return user;
    }
    
    public static Credential createCredential(User user, String passwordHash) {
        Credential credential = new Credential();
        credential.id = UUID.randomUUID();
        credential.user = user;
        credential.passwordHash = passwordHash;
        credential.createdAt = OffsetDateTime.now();
        credential.updatedAt = OffsetDateTime.now();
        return credential;
    }
    
    public static Role createUserRole() {
        Role role = new Role();
        role.id = UUID.randomUUID();
        role.name = "USER";
        role.description = "Default user role";
        role.createdAt = OffsetDateTime.now();
        return role;
    }
    
    public static Role createAdminRole() {
        Role role = new Role();
        role.id = UUID.randomUUID();
        role.name = "ADMIN";
        role.description = "Administrator role";
        role.createdAt = OffsetDateTime.now();
        return role;
    }
    
    public static RefreshToken createRefreshToken(User user, String token) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.id = UUID.randomUUID();
        refreshToken.user = user;
        refreshToken.token = token;
        refreshToken.expiresAt = OffsetDateTime.now().plusDays(7);
        refreshToken.createdAt = OffsetDateTime.now();
        return refreshToken;
    }
    
    public static RefreshToken createExpiredRefreshToken(User user, String token) {
        RefreshToken refreshToken = createRefreshToken(user, token);
        refreshToken.expiresAt = OffsetDateTime.now().minusDays(1);
        return refreshToken;
    }
    
    public static PasswordResetToken createPasswordResetToken(User user, String token) {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.id = UUID.randomUUID();
        resetToken.user = user;
        resetToken.token = token;
        resetToken.expiresAt = OffsetDateTime.now().plusHours(1);
        resetToken.createdAt = OffsetDateTime.now();
        return resetToken;
    }
    
    public static ForgotPasswordRequest createForgotPasswordRequest(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.email = email;
        return request;
    }
    
    public static ResetPasswordRequest createResetPasswordRequest(String token, String password) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.token = token;
        request.password = password;
        request.confirmPassword = password;
        return request;
    }
    
    public static ValidateTokenRequest createValidateTokenRequest(String token) {
        ValidateTokenRequest request = new ValidateTokenRequest();
        request.setToken(token);
        return request;
    }
    
    public static RefreshTokenRequest createRefreshTokenRequest(String token) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.refreshToken = token;
        return request;
    }
}