package br.com.vortex.application.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Validação local, sem rede, dos access tokens emitidos pelo serviço de autorização. Serve só aos
 * fallbacks do {@link AuthServiceClient}, quando o serviço de autorização não responde.
 *
 * <p>O serviço de autorização (Quarkus, smallrye-jwt) assina em RS256 com a chave privada dele, então
 * a conferência local usa a chave PÚBLICA correspondente: assinatura, emissor e expiração. O
 * {@code jwt.secret} HMAC desta aplicação não entra aqui; antes entrava, e o resultado era recusar todo
 * token legítimo e aceitar qualquer HS256 forjado com o segredo padrão.
 *
 * <p>Falha fechada: sem chave configurada (ou com chave ilegível) todo token é inválido. O que a
 * validação local não enxerga é o estado do servidor (logout e usuário desativado depois da emissão),
 * por isso ela só vale enquanto o serviço de autorização está fora.
 */
@Component
@Slf4j
public class AuthTokenVerifier {

    /** Emissor padrão do serviço de autorização ({@code auth.jwt.issuer} dele, {@code JWT_ISSUER}). */
    public static final String DEFAULT_ISSUER = "vortex-auth-service";

    private static final long CLOCK_SKEW_SECONDS = 30;
    private static final String PEM_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_FOOTER = "-----END PUBLIC KEY-----";

    private final PublicKey publicKey;
    private final String issuer;

    /**
     * @param resourceLoader resolve os prefixos {@code classpath:} e {@code file:}
     * @param publicKeyLocation PEM X.509 ({@code BEGIN PUBLIC KEY}) do serviço de autorização: caminho de
     *                          arquivo, ou {@code classpath:}/{@code file:}; vazio desliga a validação local
     * @param issuer emissor exigido no {@code iss}; vazio volta ao padrão {@value #DEFAULT_ISSUER}
     */
    @Autowired
    public AuthTokenVerifier(ResourceLoader resourceLoader,
                             @Value("${auth.jwt.public-key-location:}") String publicKeyLocation,
                             @Value("${auth.jwt.issuer:" + DEFAULT_ISSUER + "}") String issuer) {
        this(loadPublicKey(resourceLoader, publicKeyLocation), issuer);
    }

    /**
     * @param publicKey chave pública RSA do serviço de autorização; {@code null} recusa todo token
     * @param issuer emissor exigido no {@code iss}; vazio volta ao padrão {@value #DEFAULT_ISSUER}
     */
    public AuthTokenVerifier(PublicKey publicKey, String issuer) {
        this.publicKey = publicKey;
        this.issuer = issuer == null || issuer.isBlank() ? DEFAULT_ISSUER : issuer.trim();
        if (publicKey == null) {
            log.warn("Auth public key not configured (auth.jwt.public-key-location / AUTH_JWT_PUBLIC_KEY_LOCATION): "
                    + "while the auth service is unavailable every token will be rejected");
        }
    }

    /** {@code true} quando há chave pública carregada; sem ela {@link #verify(String)} recusa tudo. */
    public boolean isEnabled() {
        return publicKey != null;
    }

    /**
     * Confere o token como o {@code validate-token} do serviço de autorização confere, menos a consulta ao
     * banco: assinatura RS256 com a chave pública, emissor, expiração presente e no futuro, e só access
     * token (o refresh token não leva {@code email}). Um claim {@code active=false} também recusa.
     *
     * @return os dados do usuário quando o token é válido; vazio em qualquer outro caso
     */
    public Optional<VerifiedToken> verify(String token) {
        if (publicKey == null || token == null || token.isBlank()) {
            return Optional.empty();
        }
        Claims claims;
        try {
            // verifyWith(PublicKey) só aceita JWS assinado com a chave privada par desta (RS*/PS*):
            // HS256, alg=none e outra chave RSA caem aqui como JwtException.
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(issuer)
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Local token validation rejected the token: {}", e.getMessage());
            return Optional.empty();
        }

        // O jjwt não exige exp: sem ele o token não venceria nunca
        if (claims.getExpiration() == null) {
            return Optional.empty();
        }
        String subject = claims.getSubject();
        String email = stringClaim(claims, "email");
        if (subject == null || subject.isBlank() || email == null || "refresh".equals(stringClaim(claims, "type"))) {
            return Optional.empty();
        }
        if (Boolean.FALSE.equals(claims.get("active"))) {
            return Optional.empty();
        }
        return Optional.of(new VerifiedToken(subject, stringClaim(claims, "username"), email, roles(claims)));
    }

    private static String stringClaim(Claims claims, String name) {
        return claims.get(name) instanceof String value ? value : null;
    }

    private static List<String> roles(Claims claims) {
        if (claims.get("roles") instanceof Collection<?> roles) {
            return roles.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }

    private static PublicKey loadPublicKey(ResourceLoader resourceLoader, String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String trimmed = location.trim();
        Resource resource = trimmed.startsWith(ResourceLoader.CLASSPATH_URL_PREFIX) || trimmed.startsWith("file:")
                ? resourceLoader.getResource(trimmed)
                : new FileSystemResource(trimmed);
        try (InputStream in = resource.getInputStream()) {
            PublicKey key = parsePem(new String(in.readAllBytes(), StandardCharsets.US_ASCII));
            log.info("Auth public key loaded from {} for local token validation", trimmed);
            return key;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            // Falha fechada também aqui: a aplicação sobe, mas o fallback recusa todo token
            log.error("Could not load the auth public key from {}: {}. Local token validation is disabled",
                    trimmed, e.getMessage());
            return null;
        }
    }

    /** PEM X.509 SubjectPublicKeyInfo, o formato do {@code publicKey.pem} do serviço de autorização. */
    static PublicKey parsePem(String pem) throws GeneralSecurityException {
        int start = pem.indexOf(PEM_HEADER);
        int end = pem.indexOf(PEM_FOOTER);
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("expected a PEM block '" + PEM_HEADER + "'");
        }
        String base64 = pem.substring(start + PEM_HEADER.length(), end).replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    /**
     * Dados do usuário lidos de um token conferido.
     *
     * @param userId {@code sub} (id do usuário no serviço de autorização)
     * @param username claim {@code username}, pode faltar
     * @param email claim {@code email}
     * @param roles claim {@code roles}, vazio quando falta
     */
    public record VerifiedToken(String userId, String username, String email, List<String> roles) {
    }
}
