package br.com.vortex.authorization.service;

import br.com.vortex.authorization.security.PasswordService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for password validation logic without Quarkus context
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimplePasswordServiceTest {
    
    private PasswordService passwordService;
    
    @BeforeEach
    void setUp() {
        // Create a simple instance for testing basic logic
        passwordService = new PasswordService();
        
        // Set default values for testing (normally injected by Quarkus)
        try {
            var minLengthField = PasswordService.class.getDeclaredField("minLength");
            minLengthField.setAccessible(true);
            minLengthField.setInt(passwordService, 8);
            
            var maxLengthField = PasswordService.class.getDeclaredField("maxLength");
            maxLengthField.setAccessible(true);
            maxLengthField.setInt(passwordService, 128);
            
            var requireUppercaseField = PasswordService.class.getDeclaredField("requireUppercase");
            requireUppercaseField.setAccessible(true);
            requireUppercaseField.setBoolean(passwordService, true);
            
            var requireLowercaseField = PasswordService.class.getDeclaredField("requireLowercase");
            requireLowercaseField.setAccessible(true);
            requireLowercaseField.setBoolean(passwordService, true);
            
            var requireNumbersField = PasswordService.class.getDeclaredField("requireNumbers");
            requireNumbersField.setAccessible(true);
            requireNumbersField.setBoolean(passwordService, true);
            
            var requireSpecialCharsField = PasswordService.class.getDeclaredField("requireSpecialChars");
            requireSpecialCharsField.setAccessible(true);
            requireSpecialCharsField.setBoolean(passwordService, true);
            
            var specialCharsField = PasswordService.class.getDeclaredField("specialChars");
            specialCharsField.setAccessible(true);
            specialCharsField.set(passwordService, "!@#$%^&*()_+-=[]{}|;:,.<>?");
            
        } catch (Exception e) {
            fail("Failed to set up test configuration: " + e.getMessage());
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Should hash password successfully")
    void testHashPassword() {
        String password = "Test@Password123";
        
        String hashedPassword = passwordService.hashPassword(password);
        
        assertNotNull(hashedPassword);
        assertNotEquals(password, hashedPassword);
        assertTrue(hashedPassword.startsWith("$2a$"));
    }
    
    @Test
    @Order(2)
    @DisplayName("Should verify correct password")
    void testVerifyPasswordSuccess() {
        String password = "Test@Password123";
        String hashedPassword = passwordService.hashPassword(password);
        
        boolean isValid = passwordService.verifyPassword(password, hashedPassword);
        
        assertTrue(isValid);
    }
    
    @Test
    @Order(3)
    @DisplayName("Should reject incorrect password")
    void testVerifyPasswordFailure() {
        String password = "Test@Password123";
        String wrongPassword = "Wrong@Password123";
        String hashedPassword = passwordService.hashPassword(password);
        
        boolean isValid = passwordService.verifyPassword(wrongPassword, hashedPassword);
        
        assertFalse(isValid);
    }
    
    @Test
    @Order(4)
    @DisplayName("Should generate different hashes for same password")
    void testHashPasswordUniqueness() {
        String password = "Test@Password123";
        
        String hash1 = passwordService.hashPassword(password);
        String hash2 = passwordService.hashPassword(password);
        
        assertNotEquals(hash1, hash2);
        // But both should verify correctly
        assertTrue(passwordService.verifyPassword(password, hash1));
        assertTrue(passwordService.verifyPassword(password, hash2));
    }
    
    @ParameterizedTest
    @Order(5)
    @DisplayName("Should validate strong passwords")
    @ValueSource(strings = {
        "Test@Password123",
        "Strong#Pass999",
        "Valid$Pass2024",
        "Complex!Pass88"
    })
    void testValidPasswordPatterns(String password) {
        boolean isValid = passwordService.isValidPassword(password);
        assertTrue(isValid, "Password should be valid: " + password);
    }
    
    @ParameterizedTest
    @Order(6)
    @DisplayName("Should reject weak passwords")
    @ValueSource(strings = {
        "short",              // Too short
        "nouppercase123!",    // No uppercase
        "NOLOWERCASE123!",    // No lowercase
        "NoNumbers!",         // No numbers
        "NoSpecialChars123",  // No special characters
        "        ",           // Only spaces
        ""                    // Empty
    })
    void testInvalidPasswordPatterns(String password) {
        boolean isValid = passwordService.isValidPassword(password);
        assertFalse(isValid, "Password should be invalid: " + password);
    }
    
    @Test
    @Order(7)
    @DisplayName("Should reject null password")
    void testNullPassword() {
        boolean isValid = passwordService.isValidPassword(null);
        assertFalse(isValid);
    }
    
    @Test
    @Order(8)
    @DisplayName("Should reject password exceeding max length")
    void testPasswordTooLong() {
        String longPassword = "A".repeat(129) + "1!";
        
        boolean isValid = passwordService.isValidPassword(longPassword);
        
        assertFalse(isValid);
    }
    
    @Test
    @Order(9)
    @DisplayName("Should provide meaningful password requirements message")
    void testPasswordRequirements() {
        String requirements = passwordService.getPasswordRequirements();
        
        assertNotNull(requirements);
        assertTrue(requirements.contains("8-128 characters"));
        assertTrue(requirements.contains("uppercase"));
        assertTrue(requirements.contains("lowercase"));
        assertTrue(requirements.contains("number"));
        assertTrue(requirements.contains("special character"));
    }
    
    @Test
    @Order(10)
    @DisplayName("Should generate secure random token")
    void testGenerateSecureToken() {
        int tokenLength = 32;
        
        String token = passwordService.generateSecureToken(tokenLength);
        
        assertNotNull(token);
        assertEquals(tokenLength, token.length());
        // Should only contain alphanumeric characters
        assertTrue(token.matches("^[A-Za-z0-9]+$"));
    }
    
    @Test
    @Order(11)
    @DisplayName("Should generate unique tokens")
    void testGenerateUniqueTokens() {
        String token1 = passwordService.generateSecureToken(32);
        String token2 = passwordService.generateSecureToken(32);
        
        assertNotEquals(token1, token2);
    }
    
    @Test
    @Order(12)
    @DisplayName("Should generate tokens of different lengths")
    void testGenerateTokenDifferentLengths() {
        String shortToken = passwordService.generateSecureToken(16);
        String mediumToken = passwordService.generateSecureToken(32);
        String longToken = passwordService.generateSecureToken(64);
        
        assertEquals(16, shortToken.length());
        assertEquals(32, mediumToken.length());
        assertEquals(64, longToken.length());
    }
    
    @Test
    @Order(13)
    @DisplayName("Should handle password with minimum valid length")
    void testMinimumLengthPassword() {
        String minPassword = "Test@12!"; // Exactly 8 characters
        
        boolean isValid = passwordService.isValidPassword(minPassword);
        
        assertTrue(isValid);
    }
    
    @Test
    @Order(14)
    @DisplayName("Should handle password with maximum valid length")
    void testMaximumLengthPassword() {
        // Create a 128-character password
        String maxPassword = "A".repeat(120) + "a1@Test!"; // Exactly 128 characters
        
        boolean isValid = passwordService.isValidPassword(maxPassword);
        
        assertTrue(isValid);
    }
    
    @Test
    @Order(15)
    @DisplayName("Should validate BCrypt hash format")
    void testBCryptHashFormat() {
        String password = "Test@Password123";
        String hash = passwordService.hashPassword(password);
        
        // BCrypt hashes should follow the pattern $2a$rounds$salt.hash
        assertTrue(hash.matches("^\\$2a\\$\\d{2}\\$.{53}$"), 
            "Hash should match BCrypt format: " + hash);
        
        // Should contain exactly 60 characters
        assertEquals(60, hash.length());
    }
}