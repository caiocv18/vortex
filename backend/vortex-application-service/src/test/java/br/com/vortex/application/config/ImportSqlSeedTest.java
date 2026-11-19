package br.com.vortex.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * O import.sql do H2 (perfis padrão, dev e test) é lido pelo Hibernate no create-drop, que registra e PULA o statement
 * que falha, sem derrubar o boot. Sem este teste, um seed quebrado só apareceria como tela vazia no modo leve.
 * Confere as 10 entradas sem valor de venda, as 10 saídas com valor e as sequences depois do seed.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:import-sql-seed-${random.uuid};DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ImportSqlSeedTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void seedsTenTypesAndTwentyProducts() {
        assertEquals(10, count("SELECT COUNT(*) FROM tipo_produto"));
        assertEquals(20, count("SELECT COUNT(*) FROM produto"));
    }

    @Test
    void seedsTenEntriesWithoutSalePriceAndTenExitsWithIt() {
        assertEquals(10, count("SELECT COUNT(*) FROM movimento_estoque WHERE tipo_movimentacao = 'ENTRADA'"
                + " AND valor_venda IS NULL AND id BETWEEN 1 AND 10"));
        assertEquals(10, count("SELECT COUNT(*) FROM movimento_estoque WHERE tipo_movimentacao = 'SAIDA'"
                + " AND valor_venda IS NOT NULL AND id BETWEEN 11 AND 20"));
        assertEquals(20, count("SELECT COUNT(*) FROM movimento_estoque"));
    }

    @Test
    void keepsTheTimestampsAndValuesOfTheSeed() {
        assertEquals("2023-01-01 10:00:00", jdbc.queryForObject(
                "SELECT FORMATDATETIME(data_movimento, 'yyyy-MM-dd HH:mm:ss') FROM movimento_estoque WHERE id = 1",
                String.class));
        assertEquals(0, new BigDecimal("60.75").compareTo(jdbc.queryForObject(
                "SELECT valor_venda FROM movimento_estoque WHERE id = 19", BigDecimal.class)));
    }

    @Test
    void sequencesStartAfterTheSeed() {
        assertEquals(21L, jdbc.queryForObject("SELECT NEXT VALUE FOR movimento_estoque_seq", Long.class));
        assertEquals(21L, jdbc.queryForObject("SELECT NEXT VALUE FOR produto_seq", Long.class));
        assertEquals(11L, jdbc.queryForObject("SELECT NEXT VALUE FOR tipo_produto_seq", Long.class));
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? -1 : n;
    }
}
