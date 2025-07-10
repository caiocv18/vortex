package br.com.vortex.authorization.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.regex.Pattern;

@ApplicationScoped
public class PasswordService {

    @ConfigProperty(name = "auth.password.min-length")
    int minLength;

    @ConfigProperty(name = "auth.password.max-length")
    int maxLength;

    @ConfigProperty(name = "auth.password.require-uppercase")
    boolean requireUppercase;

    @ConfigProperty(name = "auth.password.require-lowercase")
    boolean requireLowercase;

    @ConfigProperty(name = "auth.password.require-numbers")
    boolean requireNumbers;

    @ConfigProperty(name = "auth.password.require-special-chars")
    boolean requireSpecialChars;

    @ConfigProperty(name = "auth.password.special-chars")
    String specialChars;

    private static final int BCRYPT_ROUNDS = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    public boolean verifyPassword(String password, String hashedPassword) {
        return BCrypt.checkpw(password, hashedPassword);
    }

    public boolean isValidPassword(String password) {
        if (password == null || password.length() < minLength || password.length() > maxLength) {
            return false;
        }

        if (requireUppercase && !Pattern.compile("[A-Z]").matcher(password).find()) {
            return false;
        }

        if (requireLowercase && !Pattern.compile("[a-z]").matcher(password).find()) {
            return false;
        }

        if (requireNumbers && !Pattern.compile("[0-9]").matcher(password).find()) {
            return false;
        }

        if (requireSpecialChars) {
            String specialCharsRegex = "[" + Pattern.quote(specialChars) + "]";
            if (!Pattern.compile(specialCharsRegex).matcher(password).find()) {
                return false;
            }
        }

        return true;
    }

    public String getPasswordRequirements() {
        StringBuilder requirements = new StringBuilder();
        requirements.append("Password must be ").append(minLength).append("-").append(maxLength).append(" characters long");

        if (requireUppercase) {
            requirements.append(", contain at least one uppercase letter");
        }
        if (requireLowercase) {
            requirements.append(", contain at least one lowercase letter");
        }
        if (requireNumbers) {
            requirements.append(", contain at least one number");
        }
        if (requireSpecialChars) {
            requirements.append(", contain at least one special character (").append(specialChars).append(")");
        }

        return requirements.toString();
    }

    public String generateSecureToken(int length) {
        StringBuilder token = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        
        for (int i = 0; i < length; i++) {
            token.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        
        return token.toString();
    }
}