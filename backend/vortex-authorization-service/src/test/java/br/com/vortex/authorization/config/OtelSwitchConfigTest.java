package br.com.vortex.authorization.config;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * O liga-desliga do OpenTelemetry no auth: {@code VORTEX_OTEL_ENABLED} (o mesmo do app) vira
 * {@code quarkus.otel.sdk.disabled} com o sinal invertido, por expressão aninhada. A suíte do Quarkus não lê o
 * application.properties principal (o de src/test/resources o encobre), então as linhas são lidas do arquivo
 * que vai no jar e resolvidas pelo SmallRye Config, o mesmo motor do Quarkus.
 */
class OtelSwitchConfigTest {

    private static final Path MAIN_PROPERTIES = Path.of("src", "main", "resources", "application.properties");
    private static final String SDK_DISABLED = "quarkus.otel.sdk.disabled";

    @Test
    void theSdkStaysDisabledWithoutTheVariable() {
        assertEquals(Boolean.TRUE, configWith(Map.of()).getValue(SDK_DISABLED, Boolean.class));
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true"})
    void theVariableTurnsTheSdkOnAndOff(String enabled, boolean expectedDisabled) {
        SmallRyeConfig config = configWith(Map.of("VORTEX_OTEL_ENABLED", enabled));

        assertEquals(expectedDisabled, config.getValue(SDK_DISABLED, Boolean.class));
    }

    /** Valor que não é true nem false não cai num padrão calado: o boot falha. */
    @Test
    void anUnknownValueFailsInsteadOfGuessing() {
        SmallRyeConfig config = configWith(Map.of("VORTEX_OTEL_ENABLED", "yes"));

        assertThrows(RuntimeException.class, () -> config.getValue(SDK_DISABLED, Boolean.class));
    }

    @Test
    void theOtlpExportGoesOverHttpToTheSameEndpointAsTheApp() {
        SmallRyeConfig config = configWith(Map.of());

        assertEquals("http/protobuf", config.getValue("quarkus.otel.exporter.otlp.protocol", String.class));
        assertEquals("http://localhost:4318", config.getValue("quarkus.otel.exporter.otlp.endpoint", String.class));
    }

    private static SmallRyeConfig configWith(Map<String, String> environment) {
        Map<String, String> properties = new HashMap<>();
        for (String key : new String[] {SDK_DISABLED, "vortex.otel.sdk-disabled-when-enabled.true",
                "vortex.otel.sdk-disabled-when-enabled.false", "quarkus.otel.exporter.otlp.protocol",
                "quarkus.otel.exporter.otlp.endpoint"}) {
            properties.put(key, mainProperty(key));
        }
        // Os interceptores padrão são os que expandem ${...}, como no Quarkus
        return new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new PropertiesConfigSource(properties, "main application.properties", 250))
                .withSources(new PropertiesConfigSource(environment, "environment", 300))
                .build();
    }

    private static String mainProperty(String key) {
        Properties file = new Properties();
        try (Reader reader = Files.newBufferedReader(MAIN_PROPERTIES, StandardCharsets.UTF_8)) {
            file.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        String value = file.getProperty(key);
        if (value == null) {
            throw new IllegalStateException(key + " sumiu de " + MAIN_PROPERTIES);
        }
        return value;
    }
}
