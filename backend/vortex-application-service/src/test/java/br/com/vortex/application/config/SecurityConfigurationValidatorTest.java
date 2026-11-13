package br.com.vortex.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * O perfil {@code cloud} conta como produção para o validador: o boot quebra com o segredo JWT que vem de fábrica
 * na base ou com H2 em memória, e passa com PostgreSQL e um segredo forte. Fora de produção o mesmo segredo só
 * gera aviso. Os segredos fortes são gerados na hora, para nenhum valor fixo parecer credencial.
 */
class SecurityConfigurationValidatorTest {

    private static final String POSTGRES_URL = "jdbc:postgresql://localhost:5432/vortex";
    private static final String H2_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    void cloudRefusesTheJwtSecretTheBaseProfileShipsWith() throws IOException {
        String shippedDefault = shippedDefaultJwtSecret();

        assertThrows(IllegalStateException.class, () -> validate("cloud", shippedDefault, POSTGRES_URL));
    }

    @Test
    void cloudRefusesAnInMemoryH2() {
        assertThrows(IllegalStateException.class, () -> validate("cloud", strongSecret(), H2_URL));
    }

    @Test
    void cloudAcceptsPostgresWithAStrongSecret() {
        assertDoesNotThrow(() -> validate("cloud", strongSecret(), POSTGRES_URL));
    }

    @Test
    void devOnlyWarnsAboutTheShippedJwtSecret() throws IOException {
        String shippedDefault = shippedDefaultJwtSecret();

        assertDoesNotThrow(() -> validate("dev", shippedDefault, H2_URL));
    }

    private static void validate(String profile, String jwtSecret, String datasourceUrl) {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret(jwtSecret);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", datasourceUrl)
                .withProperty("spring.datasource.password", strongSecret())
                .withProperty("spring.datasource.hikari.maximum-pool-size", "5");
        environment.setActiveProfiles(profile);

        new SecurityConfigurationValidator(jwt, environment).run(null);
    }

    /** O valor depois dos dois-pontos em {@code jwt.secret=${JWT_SECRET:...}} do application.properties. */
    private static String shippedDefaultJwtSecret() throws IOException {
        String raw = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))
                .getProperty("jwt.secret").trim();
        return raw.substring(raw.indexOf(':') + 1, raw.length() - 1);
    }

    private static String strongSecret() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
