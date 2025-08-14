package br.com.vortex.authorization;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile specifically configured for password recovery flow testing.
 * Provides optimized settings for testing password reset functionality
 * with appropriate database configuration and security settings.
 */
public class PasswordRecoveryTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new java.util.HashMap<>();
        
        // Database configuration for testing
        config.put("quarkus.datasource.db-kind", "h2");
        config.put("quarkus.datasource.jdbc.url", "jdbc:h2:mem:password-recovery-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        config.put("quarkus.datasource.username", "sa");
        config.put("quarkus.datasource.password", "");
        
        // Hibernate configuration for testing
        config.put("quarkus.hibernate-orm.database.generation", "drop-and-create");
        config.put("quarkus.hibernate-orm.sql-load-script", "import-test-password-recovery.sql");
        config.put("quarkus.hibernate-orm.log.sql", "false");
        
        // Flyway configuration - disable for test profile
        config.put("quarkus.flyway.migrate-at-start", "false");
        
        // Password policy configuration for testing
        config.put("auth.password.min-length", "8");
        config.put("auth.password.max-length", "128");
        config.put("auth.password.require-uppercase", "true");
        config.put("auth.password.require-lowercase", "true");
        config.put("auth.password.require-numbers", "true");
        config.put("auth.password.require-special-chars", "true");
        config.put("auth.password.special-chars", "!@#$%^&*()-_=+[]{}|;:,.<>?");
        
        // JWT configuration for testing
        config.put("mp.jwt.verify.issuer", "https://vortex-auth-test");
        config.put("mp.jwt.verify.publickey.location", "publicKey.pem");
        config.put("auth.jwt.access-token.expiration", "3600");
        config.put("auth.jwt.refresh-token.expiration", "604800");
        
        // Rate limiting configuration for testing
        config.put("auth.rate-limit.login.max-attempts", "5");
        config.put("auth.rate-limit.login.window-minutes", "15");
        config.put("auth.rate-limit.password-reset.max-attempts", "3");
        config.put("auth.rate-limit.password-reset.window-minutes", "60");
        
        // Token configuration for testing
        config.put("auth.password-reset.token-length", "32");
        config.put("auth.password-reset.token-expiration-hours", "1");
        
        // Email configuration (disabled for testing)
        config.put("auth.email.enabled", "false");
        config.put("auth.email.smtp.host", "localhost");
        config.put("auth.email.smtp.port", "587");
        config.put("auth.email.from", "test@vortex.com");
        
        // Event publishing configuration for testing
        config.put("auth.events.enabled", "true");
        config.put("auth.events.async", "false");
        
        // Logging configuration for testing
        config.put("quarkus.log.level", "INFO");
        config.put("quarkus.log.category.\"br.com.vortex.authorization\".level", "DEBUG");
        config.put("quarkus.log.category.\"org.hibernate.SQL\".level", "WARN");
        config.put("quarkus.log.category.\"org.hibernate.type.descriptor.sql\".level", "WARN");
        
        // Transaction configuration for testing
        config.put("quarkus.transaction-manager.default-transaction-timeout", "30s");
        
        // Test-specific security configurations
        config.put("auth.security.bcrypt-rounds", "4"); // Faster for testing
        config.put("auth.security.token-cleanup-enabled", "true");
        config.put("auth.security.audit-enabled", "true");
        
        // CORS configuration for testing
        config.put("quarkus.http.cors", "true");
        config.put("quarkus.http.cors.origins", "http://localhost:3001,http://localhost:5173");
        config.put("quarkus.http.cors.methods", "GET,PUT,POST,DELETE,OPTIONS");
        config.put("quarkus.http.cors.headers", "accept,authorization,content-type,x-requested-with");
        config.put("quarkus.http.cors.exposed-headers", "Content-Disposition");
        config.put("quarkus.http.cors.access-control-max-age", "3600");
        
        // OpenAPI configuration for testing
        config.put("mp.openapi.extensions.smallrye.info.title", "Vortex Authorization Service - Password Recovery Tests");
        config.put("mp.openapi.extensions.smallrye.info.version", "1.0.0-test");
        config.put("mp.openapi.extensions.smallrye.info.description", "Password recovery flow testing API");
        
        // Health check configuration for testing
        config.put("quarkus.smallrye-health.ui.enable", "false");
        
        // Metrics configuration for testing
        config.put("quarkus.micrometer.enabled", "false");
        
        // Dev services configuration (disable for tests)
        config.put("quarkus.devservices.enabled", "false");
        
        return config;
    }

    @Override
    public String getConfigProfile() {
        return "password-recovery-test";
    }
}