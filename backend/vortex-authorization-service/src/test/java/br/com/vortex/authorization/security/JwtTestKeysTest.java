package br.com.vortex.authorization.security;

import br.com.vortex.authorization.entity.User;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A suíte assina e verifica tokens só com o par de teste de src/test/resources/jwt-test. As chaves
 * reais (src/main/resources/jwt) ficam fora do git e não podem ser exigidas para os testes rodarem.
 */
@QuarkusTest
class JwtTestKeysTest {

    private static final String TEST_PRIVATE_KEY = "jwt-test/privateKey.pem";
    private static final String TEST_PUBLIC_KEY = "jwt-test/publicKey.pem";

    @Inject
    @ConfigProperty(name = "smallrye.jwt.sign.key.location")
    String signKeyLocation;

    @Inject
    @ConfigProperty(name = "mp.jwt.verify.publickey.location")
    String verifyKeyLocation;

    @Inject
    JwtService jwtService;

    @Inject
    JWTParser jwtParser;

    @Test
    @DisplayName("The test config overrides both key locations of the main application.properties")
    void testConfigPointsAtTheTestKeyPair() {
        assertEquals(TEST_PRIVATE_KEY, signKeyLocation);
        assertEquals(TEST_PUBLIC_KEY, verifyKeyLocation);
    }

    @Test
    @DisplayName("Tokens minted by JwtService verify against the test public key read straight from jwt-test")
    void tokensAreSignedWithTheTestPrivateKey() throws Exception {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = "keys@test.com";
        user.username = "keys";
        user.isActive = true;
        user.isVerified = true;

        String token = jwtService.generateAccessToken(user);

        JsonWebToken jwt = jwtParser.verify(token, readPublicKey(TEST_PUBLIC_KEY));
        assertEquals(user.id.toString(), jwt.getSubject());
    }

    private static PublicKey readPublicKey(String resource) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource + " precisa estar no classpath de teste");
            String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
        }
    }
}
