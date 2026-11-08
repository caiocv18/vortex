package br.com.vortex.application.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityConfigurationValidator implements ApplicationRunner {

    private final JwtProperties jwtProperties;
    private final Environment environment;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Validating security configuration...");
        
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        validateJwtConfiguration(warnings, errors);
        validateDatabaseConfiguration(warnings, errors);
        validateProfileSpecificSettings(warnings, errors);
        validateEnvironmentVariables(warnings, errors);

        // Log warnings
        if (!warnings.isEmpty()) {
            log.warn("Security configuration warnings detected:");
            warnings.forEach(warning -> log.warn("⚠️  {}", warning));
        }

        // Log errors and fail if any exist
        if (!errors.isEmpty()) {
            log.error("Critical security configuration errors detected:");
            errors.forEach(error -> log.error("❌ {}", error));
            throw new IllegalStateException("Application startup failed due to security configuration errors");
        }

        log.info("✅ Security configuration validation completed successfully");
    }

    private void validateJwtConfiguration(List<String> warnings, List<String> errors) {
        // Check JWT secret
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.trim().isEmpty()) {
            errors.add("JWT secret is not configured");
        } else if (secret.length() < 32) {
            errors.add("JWT secret is too short (minimum 32 characters required)");
        } else if (isWeakSecret(secret)) {
            if (isProductionEnvironment()) {
                errors.add("JWT secret is too weak - use a cryptographically strong random value");
            } else {
                warnings.add("JWT secret uses the default development value - replace with a strong secret for production");
            }
        }

        // Check expiration time
        if (jwtProperties.getExpiration() > 24 * 60 * 60 * 1000) { // 24 hours
            warnings.add("JWT expiration time is longer than 24 hours - consider shorter expiration for better security");
        }

        // Production-specific checks
        if (isProductionEnvironment()) {
            if (!jwtProperties.getStrictValidation()) {
                errors.add("JWT strict validation must be enabled in production");
            }
        }
    }

    private void validateDatabaseConfiguration(List<String> warnings, List<String> errors) {
        if (isProductionEnvironment()) {
            String datasourceUrl = environment.getProperty("spring.datasource.url", "");
            String datasourcePassword = environment.getProperty("spring.datasource.password", "");

            // Check for H2 database in production
            if (datasourceUrl.contains("h2:mem:")) {
                errors.add("H2 in-memory database should not be used in production");
            }

            // Check for weak database passwords
            if (datasourcePassword.isEmpty()) {
                errors.add("Database password is not configured");
            } else if (isWeakPassword(datasourcePassword)) {
                warnings.add("Database password appears to be weak - consider using a stronger password");
            }

            // Check connection pool settings
            Integer maxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class);
            if (maxPoolSize == null || maxPoolSize > 50) {
                warnings.add("Database connection pool size should be tuned for production workload");
            }
        }
    }

    private void validateProfileSpecificSettings(List<String> warnings, List<String> errors) {
        if (isProductionEnvironment()) {
            // Check logging levels
            String rootLogLevel = environment.getProperty("logging.level.root", "INFO");
            if ("DEBUG".equalsIgnoreCase(rootLogLevel) || "TRACE".equalsIgnoreCase(rootLogLevel)) {
                warnings.add("Debug logging is enabled in production - may impact performance and expose sensitive information");
            }

            // Check if H2 console is enabled
            Boolean h2ConsoleEnabled = environment.getProperty("spring.h2.console.enabled", Boolean.class, false);
            if (h2ConsoleEnabled) {
                errors.add("H2 console should not be enabled in production");
            }

            // Check SQL logging
            Boolean showSql = environment.getProperty("spring.jpa.show-sql", Boolean.class, false);
            if (showSql) {
                warnings.add("SQL logging is enabled in production - may impact performance");
            }
        }
    }

    private void validateEnvironmentVariables(List<String> warnings, List<String> errors) {
        // Check for required environment variables in production
        if (isProductionEnvironment()) {
            String[] requiredEnvVars = {
                "JWT_SECRET",
                "ORACLE_DB_PASSWORD",
                "REDIS_PASSWORD"
            };

            for (String envVar : requiredEnvVars) {
                String value = System.getenv(envVar);
                if (value == null || value.trim().isEmpty()) {
                    warnings.add("Environment variable " + envVar + " is not set - using default value");
                }
            }
        }
    }

    private boolean isProductionEnvironment() {
        return activeProfiles.contains("prd") || 
               activeProfiles.contains("prod") || 
               activeProfiles.contains("production");
    }

    private boolean isWeakSecret(String secret) {
        // Check for common weak patterns
        String lowerSecret = secret.toLowerCase();
        return lowerSecret.contains("secret") ||
               lowerSecret.contains("password") ||
               lowerSecret.contains("change") ||
               lowerSecret.contains("default") ||
               lowerSecret.matches("^[a-z]+$") || // only lowercase
               lowerSecret.matches("^[0-9]+$") || // only numbers
               secret.equals(secret.toLowerCase()) && secret.length() < 50; // too simple
    }

    private boolean isWeakPassword(String password) {
        return password.length() < 12 ||
               password.toLowerCase().contains("password") ||
               password.toLowerCase().contains("admin") ||
               password.matches("^[a-zA-Z]+$") || // only letters
               password.matches("^[0-9]+$"); // only numbers
    }
}
