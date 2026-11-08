package br.com.vortex.application.service;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Optional;

import static br.com.vortex.application.service.AuthTokens.accessTokenClaims;
import static br.com.vortex.application.service.AuthTokens.fromNow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validação local dos tokens do serviço de autorização: só passa o RS256 assinado com a chave par da
 * chave pública configurada, do emissor certo e dentro da validade; sem chave, nada passa.
 */
class AuthTokenVerifierTest {

    private static final KeyPair AUTH_KEYS = AuthTokens.newRsaKeyPair();
    private static final KeyPair OTHER_KEYS = AuthTokens.newRsaKeyPair();

    private final AuthTokenVerifier verifier =
            new AuthTokenVerifier(AUTH_KEYS.getPublic(), AuthTokenVerifier.DEFAULT_ISSUER);

    @Test
    void acceptsAnRs256AccessTokenSignedByTheAuthService() {
        Optional<AuthTokenVerifier.VerifiedToken> verified = verifier.verify(AuthTokens.authServiceToken(AUTH_KEYS));

        assertTrue(verified.isPresent());
        assertEquals(AuthTokens.USER_ID, verified.get().userId());
        assertEquals(AuthTokens.USERNAME, verified.get().username());
        assertEquals(AuthTokens.EMAIL, verified.get().email());
        assertEquals(AuthTokens.ROLES, verified.get().roles());
    }

    @Test
    void rejectsAnHs256TokenForgedWithTheApplicationSecret() {
        assertTrue(verifier.verify(AuthTokens.forgedHs256Token()).isEmpty());
    }

    @Test
    void rejectsATokenSignedWithAnotherRsaKey() {
        String token = accessTokenClaims(AuthTokenVerifier.DEFAULT_ISSUER)
                .expiration(fromNow(Duration.ofMinutes(15)))
                .signWith(OTHER_KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertTrue(verifier.verify(token).isEmpty());
    }

    @Test
    void rejectsAnUnsignedToken() {
        String token = accessTokenClaims(AuthTokenVerifier.DEFAULT_ISSUER)
                .expiration(fromNow(Duration.ofMinutes(15)))
                .compact();

        assertTrue(verifier.verify(token).isEmpty());
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        String token = accessTokenClaims("https://emissor-falso.example")
                .expiration(fromNow(Duration.ofMinutes(15)))
                .signWith(AUTH_KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertTrue(verifier.verify(token).isEmpty());
    }

    @Test
    void rejectsAnExpiredToken() {
        // Bem além da tolerância de relógio de 30 s
        String token = accessTokenClaims(AuthTokenVerifier.DEFAULT_ISSUER)
                .expiration(fromNow(Duration.ofMinutes(-5)))
                .signWith(AUTH_KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertTrue(verifier.verify(token).isEmpty());
    }

    @Test
    void rejectsATokenWithoutExpiry() {
        String token = accessTokenClaims(AuthTokenVerifier.DEFAULT_ISSUER)
                .signWith(AUTH_KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertTrue(verifier.verify(token).isEmpty());
    }

    @Test
    void rejectsARefreshToken() {
        // Como o JwtService do serviço de autorização monta o refresh token: sem email, type=refresh
        String token = Jwts.builder()
                .issuer(AuthTokenVerifier.DEFAULT_ISSUER)
                .subject(AuthTokens.USER_ID)
                .claim("upn", AuthTokens.EMAIL)
                .claim("type", "refresh")
                .expiration(fromNow(Duration.ofDays(7)))
                .signWith(AUTH_KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertTrue(verifier.verify(token).isEmpty());
    }

    @Test
    void rejectsATokenOfAnInactiveUser() {
        String token = accessTokenClaims(AuthTokenVerifier.DEFAULT_ISSUER)
                .claim("active", false)
                .expiration(fromNow(Duration.ofMinutes(15)))
                .signWith(AUTH_KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertTrue(verifier.verify(token).isEmpty());
    }

    @Test
    void rejectsEveryTokenWhenNoKeyIsConfigured() {
        AuthTokenVerifier withoutKey = new AuthTokenVerifier(new DefaultResourceLoader(), "", "");

        assertFalse(withoutKey.isEnabled());
        assertTrue(withoutKey.verify(AuthTokens.authServiceToken(AUTH_KEYS)).isEmpty());
        assertTrue(withoutKey.verify(AuthTokens.forgedHs256Token()).isEmpty());
        assertTrue(new AuthTokenVerifier(null, AuthTokenVerifier.DEFAULT_ISSUER)
                .verify(AuthTokens.authServiceToken(AUTH_KEYS)).isEmpty());
    }

    @Test
    void loadsThePublicKeyFromAPemFile(@TempDir Path dir) throws IOException {
        Path pem = Files.writeString(dir.resolve("publicKey.pem"), AuthTokens.toPem(AUTH_KEYS.getPublic()));
        String token = AuthTokens.authServiceToken(AUTH_KEYS);

        AuthTokenVerifier fromPath = new AuthTokenVerifier(new DefaultResourceLoader(), pem.toString(), "");
        AuthTokenVerifier fromFileUrl = new AuthTokenVerifier(new DefaultResourceLoader(), pem.toUri().toString(), "");

        assertTrue(fromPath.isEnabled());
        assertTrue(fromPath.verify(token).isPresent());
        assertTrue(fromFileUrl.verify(token).isPresent());
        assertTrue(fromPath.verify(AuthTokens.forgedHs256Token()).isEmpty());
    }

    @Test
    void failsClosedWhenTheConfiguredKeyFileIsMissingOrNotAPublicKey(@TempDir Path dir) throws IOException {
        Path notAKey = Files.writeString(dir.resolve("notAKey.pem"), "isto não é uma chave");

        AuthTokenVerifier missing = new AuthTokenVerifier(new DefaultResourceLoader(),
                dir.resolve("inexistente.pem").toString(), "");
        AuthTokenVerifier invalid = new AuthTokenVerifier(new DefaultResourceLoader(), notAKey.toString(), "");

        assertFalse(missing.isEnabled());
        assertFalse(invalid.isEnabled());
        assertTrue(missing.verify(AuthTokens.authServiceToken(AUTH_KEYS)).isEmpty());
        assertTrue(invalid.verify(AuthTokens.authServiceToken(AUTH_KEYS)).isEmpty());
    }

    @Test
    void requiresTheConfiguredIssuer() {
        AuthTokenVerifier customIssuer = new AuthTokenVerifier(AUTH_KEYS.getPublic(), "https://auth.vortex.dev");
        String customIssuerToken = accessTokenClaims("https://auth.vortex.dev")
                .expiration(fromNow(Duration.ofMinutes(15)))
                .signWith(AUTH_KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertTrue(customIssuer.verify(customIssuerToken).isPresent());
        assertTrue(customIssuer.verify(AuthTokens.authServiceToken(AUTH_KEYS)).isEmpty());
    }
}
