package br.com.vortex.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lê o {@code application-postgres.properties} que vai na imagem, sem cópia: se alguém devolver um padrão à senha,
 * o primeiro teste deixa de falhar o boot e reprova.
 */
class DatasourcePasswordCheckTest {

    @Test
    void stopsTheBootNamingTheVariableWhenThePostgresPasswordIsNotExported() throws IOException {
        MockEnvironment environment = withProfileFile("application-postgres.properties");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> check(environment));
        assertTrue(failure.getMessage().contains("VORTEX_DB_PASSWORD"), failure.getMessage());
    }

    @Test
    void passesWhenThePostgresPasswordIsExported() throws IOException {
        MockEnvironment environment = withProfileFile("application-postgres.properties")
                .withProperty("VORTEX_DB_PASSWORD", StrongTestPasswords.generate());

        assertDoesNotThrow(() -> check(environment));
    }

    /** A base usa H2 com senha vazia por padrão: vazia é um valor, não uma variável que falta. */
    @Test
    void passesWithTheEmptyH2PasswordOfTheBaseProfile() throws IOException {
        MockEnvironment environment = withProfileFile("application.properties");

        assertDoesNotThrow(() -> check(environment));
    }

    private static MockEnvironment withProfileFile(String file) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addLast(
                new PropertiesPropertySource(file, PropertiesLoaderUtils.loadProperties(new ClassPathResource(file))));
        return environment;
    }

    /** Pelo pós-processador que o método {@code @Bean} entrega, o mesmo que o contexto chama antes dos beans. */
    private static void check(MockEnvironment environment) {
        DatasourcePasswordCheck.failFastOnUnresolvedDatasourcePassword(environment)
                .postProcessBeanFactory(new DefaultListableBeanFactory());
    }
}
