package br.com.vortex.application.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Um PostgreSQL 18 de verdade, um só por JVM de testes, para as classes que sobem o perfil {@code postgres}.
 *
 * <p>O container entra pelas mesmas variáveis que a EC2 exporta ({@code VORTEX_DB_URL}, {@code VORTEX_DB_USERNAME},
 * {@code VORTEX_DB_PASSWORD}), e não por {@code @ServiceConnection}: a conexão de serviço tem precedência sobre as
 * propriedades e traz o driver junto, então esconderia um {@code application-postgres.properties} que esquecesse
 * de sobrescrever o {@code org.h2.Driver} da base. Assim o teste exercita os placeholders e a chave do driver que
 * vão para a nuvem.
 *
 * <p>Sem Docker, o inicializador estático falha e a classe de teste erra; nunca pula em silêncio, nem no CI.
 * O Ryuk do Testcontainers derruba o container quando a JVM sai.
 */
public abstract class PostgresContainerSupport {

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.6-alpine").withDatabaseName("vortex");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void vortexDatabase(DynamicPropertyRegistry registry) {
        registry.add("VORTEX_DB_URL", POSTGRES::getJdbcUrl);
        registry.add("VORTEX_DB_USERNAME", POSTGRES::getUsername);
        registry.add("VORTEX_DB_PASSWORD", POSTGRES::getPassword);
    }
}
