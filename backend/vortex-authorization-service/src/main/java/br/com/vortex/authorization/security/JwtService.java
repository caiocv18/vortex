package br.com.vortex.authorization.security;

import br.com.vortex.authorization.entity.User;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class JwtService {

    @ConfigProperty(name = "auth.jwt.issuer")
    String issuer;

    @ConfigProperty(name = "auth.jwt.access-token-expiration")
    Duration accessTokenExpiration;

    @ConfigProperty(name = "auth.jwt.refresh-token-expiration")
    Duration refreshTokenExpiration;

    public String generateAccessToken(User user) {
        Set<String> roles = user.roles != null ? 
            user.roles.stream().map(role -> role.name).collect(Collectors.toSet()) : 
            Set.of();

        return Jwt.issuer(issuer)
            .upn(user.email)
            .subject(user.id.toString())
            .claim("username", user.username)
            .claim("email", user.email)
            .claim("roles", roles)
            .claim("active", user.isActive)
            .claim("verified", user.isVerified)
            .expiresAt(Instant.now().plus(accessTokenExpiration))
            .sign();
    }

    public String generateRefreshToken(User user) {
        return Jwt.issuer(issuer)
            .upn(user.email)
            .subject(user.id.toString())
            .claim("type", "refresh")
            .expiresAt(Instant.now().plus(refreshTokenExpiration))
            .sign();
    }

    public boolean isRefreshToken(JsonWebToken jwt) {
        return "refresh".equals(jwt.claim("type").orElse(null));
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration.getSeconds();
    }
}