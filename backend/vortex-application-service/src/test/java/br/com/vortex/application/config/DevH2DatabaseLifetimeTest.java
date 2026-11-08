package br.com.vortex.application.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * O H2 em memória do perfil dev (e do dev,leve, o RUN_MODE padrão do launcher) tem de sobreviver à
 * aposentadoria da conexão do pool. Quando a conexão passa do max-lifetime, o Hikari fecha a velha antes de
 * abrir a substituta; sem DB_CLOSE_DELAY=-1 o H2 apaga o banco ao fechar a última conexão, e a aplicação
 * passa a responder "Table PRODUTO not found" até reiniciar.
 *
 * <p>O teste usa a URL e as credenciais do application-dev.properties e o pool do application-leve.properties,
 * com o menor max-lifetime que o Hikari aceita (30 s; abaixo disso ele volta ao padrão de 30 min). Só o nome
 * do banco muda, para não dividir o testdb com outro contexto da mesma JVM. Um pool de controle, com a URL de
 * antes da correção (sem parâmetros), prova que o teste enxerga o defeito. Os dois rodam juntos, em até 30 s
 * (o HikariCP 7 antecipa a aposentadoria em até 25% do max-lifetime, ao acaso).
 */
class DevH2DatabaseLifetimeTest {

    private static final String H2_MEM_PREFIX = "jdbc:h2:mem:";
    private static final long SHORTEST_MAX_LIFETIME_MS = 30_000;
    private static final Duration RETIREMENT_TIMEOUT = Duration.ofSeconds(60);

    @Test
    void devInMemoryDatabaseKeepsItsDataWhenThePooledConnectionIsRetired() throws Exception {
        Properties dev = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-dev.properties"));
        Properties leve = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-leve.properties"));
        String devUrl = dev.getProperty("spring.datasource.url").trim();
        assertTrue(devUrl.startsWith(H2_MEM_PREFIX), "o perfil dev deveria usar H2 em memória: " + devUrl);
        String suffix = UUID.randomUUID().toString();

        try (HikariDataSource devPool = leveShapedPool("dev-leve",
                     withDatabaseName(devUrl, "dev-lifetime-" + suffix), dev, leve);
             HikariDataSource controlPool = leveShapedPool("controle-sem-delay",
                     H2_MEM_PREFIX + "dev-lifetime-control-" + suffix, dev, leve)) {

            Connection devPhysical = createTableWithOneRow(devPool);
            Connection controlPhysical = createTableWithOneRow(controlPool);

            awaitRetiredAndReplaced(devPool, devPhysical);
            awaitRetiredAndReplaced(controlPool, controlPhysical);

            try (Connection connection = devPool.getConnection()) {
                assertNotSame(devPhysical, connection.unwrap(Connection.class), "a leitura tem de vir da conexão nova");
                assertEquals(1, countRows(connection), "o dado gravado antes da troca de conexão sumiu");
            }
            try (Connection connection = controlPool.getConnection()) {
                SQLException gone = assertThrows(SQLException.class, () -> countRows(connection),
                        "controle: sem DB_CLOSE_DELAY=-1 o banco deveria ter sido apagado com a última conexão");
                assertTrue(gone.getMessage().contains("not found"), gone.getMessage());
            }
        }
    }

    /** Troca só o nome do banco, mantendo os parâmetros da URL (;DB_CLOSE_DELAY=-1 etc.). */
    private static String withDatabaseName(String h2MemUrl, String databaseName) {
        String rest = h2MemUrl.substring(H2_MEM_PREFIX.length());
        int parameters = rest.indexOf(';');
        return H2_MEM_PREFIX + databaseName + (parameters >= 0 ? rest.substring(parameters) : "");
    }

    private static HikariDataSource leveShapedPool(String poolName, String url, Properties dev, Properties leve) {
        int minimumIdle = Integer.parseInt(leve.getProperty("spring.datasource.hikari.minimum-idle").trim());
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(url);
        config.setUsername(dev.getProperty("spring.datasource.username"));
        config.setPassword(dev.getProperty("spring.datasource.password"));
        config.setMinimumIdle(minimumIdle);
        // Em repouso o pool do leve fica com minimum-idle conexões (o idle-timeout corta as extras). O teto igual
        // ao piso reproduz esse estado e impede o housekeeper de abrir outra conexão enquanto o teste segura a
        // primeira, o que só adiaria a aposentadoria da última.
        config.setMaximumPoolSize(minimumIdle);
        config.setMaxLifetime(SHORTEST_MAX_LIFETIME_MS);
        return new HikariDataSource(config);
    }

    /** Grava pela única conexão do pool e devolve a conexão física (a do H2) para acompanhar a aposentadoria. */
    private static Connection createTableWithOneRow(HikariDataSource pool) throws SQLException {
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE produto (id INT PRIMARY KEY, nome VARCHAR(50))");
            statement.execute("INSERT INTO produto VALUES (1, 'gravado antes da troca')");
            return connection.unwrap(Connection.class);
        }
    }

    /**
     * Espera, sem pegar conexão do pool (o que mudaria o cenário), o Hikari fechar a conexão física pelo
     * max-lifetime e depois abrir a substituta. O fechamento termina antes de a substituta abrir, então quando
     * ela aparece o H2 já decidiu se o banco vive ou morre.
     */
    private static void awaitRetiredAndReplaced(HikariDataSource pool, Connection physical) throws Exception {
        long deadline = System.nanoTime() + RETIREMENT_TIMEOUT.toNanos();
        while (!physical.isClosed()) {
            sleepUntil(deadline, "a conexão não foi aposentada pelo max-lifetime");
        }
        while (pool.getHikariPoolMXBean().getTotalConnections() < 1) {
            sleepUntil(deadline, "o pool não abriu a conexão substituta");
        }
    }

    private static void sleepUntil(long deadline, String timeoutMessage) throws InterruptedException {
        if (System.nanoTime() > deadline) {
            fail(timeoutMessage);
        }
        Thread.sleep(100);
    }

    private static int countRows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM produto")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
