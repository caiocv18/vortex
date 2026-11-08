package br.com.vortex.application.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
@Data
public class JwtProperties {

    /**
     * JWT secret key for signing tokens.
     * Must be at least 32 characters for security.
     */
    @NotBlank(message = "JWT secret cannot be blank")
    @Size(min = 32, message = "JWT secret must be at least 32 characters long for security")
    private String secret;

    /**
     * Token expiration time in milliseconds.
     * Default: 1 hour (3600000 ms)
     */
    @NotNull(message = "JWT expiration cannot be null")
    @Min(value = 300000, message = "JWT expiration must be at least 5 minutes (300000 ms)")
    private Long expiration = 3600000L;

    /**
     * Issuer claim for JWT tokens.
     * Default: vortex-application-service
     */
    @NotBlank(message = "JWT issuer cannot be blank")
    private String issuer = "vortex-application-service";

    /**
     * Audience claim for JWT tokens.
     * Default: vortex-users
     */
    @NotBlank(message = "JWT audience cannot be blank")
    private String audience = "vortex-users";

    /**
     * Whether to validate JWT signature strictly.
     * Default: true (should always be true in production)
     */
    @NotNull(message = "JWT strict validation flag cannot be null")
    private Boolean strictValidation = true;

    /**
     * Clock skew tolerance in seconds for token validation.
     * Default: 30 seconds
     */
    @Min(value = 0, message = "Clock skew must be non-negative")
    private Long clockSkewSeconds = 30L;
}