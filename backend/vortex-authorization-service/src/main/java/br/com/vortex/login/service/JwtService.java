package br.com.vortex.login.service;

import br.com.vortex.login.model.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class JwtService {
    
    private static final Logger log = Logger.getLogger(JwtService.class.getName());
    
    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;
    
    @ConfigProperty(name = "mp.jwt.verify.audiences")
    String audience;
    
    public String generateToken(User user) {
        try {
            Set<String> groups = user.roles.stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet());
            
            return Jwt.issuer(issuer)
                    .audience(audience)
                    .subject(user.email)
                    .groups(groups)
                    .claim("email", user.email)
                    .claim("name", user.name)
                    .claim("userId", user.id)
                    .claim("provider", user.provider)
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plus(Duration.ofHours(8)))
                    .sign();
        } catch (Exception e) {
            log.severe("Erro ao gerar token JWT para usuário: " + user.email + ", erro: " + e.getMessage());
            throw new RuntimeException("Erro ao gerar token JWT", e);
        }
    }
    
    public Object getJWKS() {
        try {
            // Simulated JWKS - in production, this should return actual public keys
            Map<String, Object> jwks = new HashMap<>();
            jwks.put("keys", Arrays.asList(
                Map.of(
                    "kty", "RSA",
                    "kid", "vortex-auth-key-1",
                    "use", "sig",
                    "alg", "RS256",
                    "n", "sample-public-key-modulus",
                    "e", "AQAB"
                )
            ));
            return jwks;
        } catch (Exception e) {
            log.severe("Erro ao obter JWKS: " + e.getMessage());
            throw new RuntimeException("Erro ao obter JWKS", e);
        }
    }
    
    public Object getOpenIdConfiguration() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("issuer", issuer);
            config.put("authorization_endpoint", issuer + "/auth/authorize");
            config.put("token_endpoint", issuer + "/auth/token");
            config.put("userinfo_endpoint", issuer + "/auth/userinfo");
            config.put("jwks_uri", issuer + "/auth/jwks.json");
            config.put("response_types_supported", Arrays.asList("code", "token", "id_token"));
            config.put("subject_types_supported", Arrays.asList("public"));
            config.put("id_token_signing_alg_values_supported", Arrays.asList("RS256"));
            config.put("scopes_supported", Arrays.asList("openid", "profile", "email"));
            config.put("token_endpoint_auth_methods_supported", Arrays.asList("client_secret_basic", "client_secret_post"));
            config.put("claims_supported", Arrays.asList("sub", "name", "email", "groups", "userId", "provider"));
            
            return config;
        } catch (Exception e) {
            log.severe("Erro ao obter configuração OpenID: " + e.getMessage());
            throw new RuntimeException("Erro ao obter configuração OpenID", e);
        }
    }
}