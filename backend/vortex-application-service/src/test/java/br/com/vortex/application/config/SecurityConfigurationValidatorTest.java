package br.com.vortex.application.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
 * na base, com H2 em memória ou com uma senha de banco fraca, e passa com PostgreSQL, um segredo forte e uma senha
 * forte. Fora de produção o mesmo segredo só gera aviso. Os segredos fortes são gerados na hora, para nenhum valor
 * fixo parecer credencial.
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
        String secret = strongSecret();

        assertThrows(IllegalStateException.class, () -> validate("cloud", secret, H2_URL));
    }

    /** Controle dos casos de recusa: o mesmo perfil, só com a senha forte, sobe. */
    @ParameterizedTest
    @ValueSource(strings = {"cloud", "prd"})
    void productionAcceptsAStrongSecretAndAStrongDatabasePassword(String profile) {
        assertDoesNotThrow(() -> validate(profile, strongSecret(), POSTGRES_URL));
    }

    @Test
    void devOnlyWarnsAboutTheShippedJwtSecret() throws IOException {
        String shippedDefault = shippedDefaultJwtSecret();

        assertDoesNotThrow(() -> validate("dev", shippedDefault, H2_URL));
    }

    /**
     * "vortex" era o padrão do perfil postgres e "secure_oracle_password" o do perfil prd, os dois dentro da imagem;
     * "vortex_password" era o padrão dos composes do prd para o usuário do app no Oracle. Antes o validador só
     * avisava, e o boot seguia com eles.
     */
    @ParameterizedTest
    @CsvSource({"cloud, vortex", "cloud, vortex_password", "prd, vortex", "prd, vortex_password",
            "prd, secure_oracle_password"})
    void productionRefusesTheDatabasePasswordsTheProfilesShippedWith(String profile, String shippedPassword) {
        String secret = strongSecret();

        assertThrows(IllegalStateException.class,
                () -> validate(profile, secret, POSTGRES_URL, shippedPassword));
    }

    @Test
    void devDoesNotJudgeTheDatabasePassword() {
        assertDoesNotThrow(() -> validate("dev", strongSecret(), H2_URL, "vortex"));
    }

    @Test
    void productionRefusesAnEnabledH2Console() {
        assertThrows(IllegalStateException.class,
                () -> validateProductionWith("spring.h2.console.enabled", "true"));
    }

    @Test
    void productionOnlyWarnsAboutSqlLogging() {
        assertDoesNotThrow(() -> validateProductionWith("spring.jpa.show-sql", "true"));
    }

    /** Produção (cloud) que passaria no validador, mais uma propriedade em teste. */
    private static void validateProductionWith(String key, String value) {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret(strongSecret());
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", POSTGRES_URL)
                .withProperty("spring.datasource.password", StrongTestPasswords.generate())
                .withProperty("spring.datasource.hikari.maximum-pool-size", "5")
                .withProperty(key, value);
        environment.setActiveProfiles("cloud");

        new SecurityConfigurationValidator(jwt, environment).run(null);
    }

    private static void validate(String profile, String jwtSecret, String datasourceUrl) {
        validate(profile, jwtSecret, datasourceUrl, StrongTestPasswords.generate());
    }

    private static void validate(String profile, String jwtSecret, String datasourceUrl, String datasourcePassword) {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret(jwtSecret);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", datasourceUrl)
                .withProperty("spring.datasource.password", datasourcePassword)
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
