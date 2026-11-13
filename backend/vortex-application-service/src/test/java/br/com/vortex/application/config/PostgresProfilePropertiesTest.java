package br.com.vortex.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * O perfil {@code postgres} é composto com outros ({@code dev,postgres}, {@code cloud,postgres,sqs}) e ganha deles
 * por vir por último. Para isso ele tem de sobrescrever toda chave de banco, JPA, inicialização e cache que o
 * {@code dev} define; uma chave esquecida faria o {@code dev,postgres} herdar um valor do H2 sem aviso.
 */
class PostgresProfilePropertiesTest {

    private static final List<String> DATABASE_PREFIXES =
            List.of("spring.datasource.", "spring.jpa.", "spring.sql.init.", "spring.cache.", "spring.h2.");

    @Test
    void postgresOverridesEveryDatabaseKeyTheDevProfileSets() throws IOException {
        Properties dev = load("application-dev.properties");
        Properties postgres = load("application-postgres.properties");

        List<String> missing = dev.stringPropertyNames().stream()
                .filter(key -> DATABASE_PREFIXES.stream().anyMatch(key::startsWith))
                .filter(key -> !postgres.containsKey(key))
                .sorted()
                .toList();

        assertEquals(List.of(), missing, "chaves do dev que o postgres não sobrescreve");
    }

    @Test
    void postgresNeverMentionsOracle() throws IOException {
        load("application-postgres.properties").forEach((key, value) -> assertFalse(
                (key + "=" + value).toLowerCase(Locale.ROOT).contains("oracle"), "Oracle no perfil postgres: " + key));
    }

    @Test
    void postgresRunsFlywayBeforeAValidatingHibernate() throws IOException {
        Properties postgres = load("application-postgres.properties");

        assertEquals("org.postgresql.Driver", postgres.getProperty("spring.datasource.driverClassName"));
        assertEquals("true", postgres.getProperty("spring.flyway.enabled"));
        assertEquals("validate", postgres.getProperty("spring.jpa.hibernate.ddl-auto"));
        // Com true (o valor da base) o JPA não espera o Flyway, e o validate pode rodar antes das tabelas existirem
        assertEquals("false", postgres.getProperty("spring.jpa.defer-datasource-initialization"));
        assertEquals("never", postgres.getProperty("spring.sql.init.mode"));
    }

    @Test
    void baseProfileKeepsFlywayOffForTheInMemoryDatabase() throws IOException {
        assertEquals("false", load("application.properties").getProperty("spring.flyway.enabled"));
    }

    private static Properties load(String resource) throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(resource));
    }
}
