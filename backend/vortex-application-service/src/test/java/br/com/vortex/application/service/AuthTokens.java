package br.com.vortex.application.service;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Tokens de teste no formato do serviço de autorização (o JwtService do Quarkus): RS256, emissor,
 * {@code sub} com o id do usuário e os claims {@code username}, {@code email}, {@code roles} e {@code active}.
 */
public final class AuthTokens {

    /** Valor padrão de {@code jwt.secret} no application.properties: público, qualquer um forja com ele. */
    public static final String APP_DEFAULT_SECRET = "CHANGE_THIS_SECURE_SECRET_AT_LEAST_32_CHARS";
    public static final String USER_ID = "7d3c1f0e-2b4a-4c8e-9f61-0a5b6c7d8e9f";
    public static final String USERNAME = "ana";
    public static final String EMAIL = "ana@vortex.dev";
    public static final List<String> ROLES = List.of("USER", "ADMIN");

    private AuthTokens() {
    }

    public static KeyPair newRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** PEM X.509 ({@code BEGIN PUBLIC KEY}), o mesmo formato do publicKey.pem do serviço de autorização. */
    public static String toPem(PublicKey key) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }

    /** Claims de um access token do serviço de autorização, ainda sem expiração nem assinatura. */
    public static JwtBuilder accessTokenClaims(String issuer) {
        return Jwts.builder()
                .issuer(issuer)
                .subject(USER_ID)
                .claim("upn", EMAIL)
                .claim("username", USERNAME)
                .claim("email", EMAIL)
                .claim("roles", ROLES)
                .claim("active", true)
                .claim("verified", true);
    }

    public static Date fromNow(Duration offset) {
        return Date.from(Instant.now().plus(offset));
    }

    /** Access token legítimo: RS256 com a chave privada do serviço de autorização, emissor padrão, 15 min. */
    public static String authServiceToken(KeyPair authKeys) {
        return accessTokenClaims(AuthTokenVerifier.DEFAULT_ISSUER)
                .expiration(fromNow(Duration.ofMinutes(15)))
                .signWith(authKeys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /** O ataque do achado: os mesmos claims, mas HS256 com o {@code jwt.secret} padrão da aplicação. */
    public static String forgedHs256Token() {
        return accessTokenClaims(AuthTokenVerifier.DEFAULT_ISSUER)
                .expiration(fromNow(Duration.ofHours(1)))
                .signWith(Keys.hmacShaKeyFor(APP_DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }
}
