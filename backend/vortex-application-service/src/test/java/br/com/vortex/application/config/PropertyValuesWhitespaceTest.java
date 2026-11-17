package br.com.vortex.application.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * O {@code .properties} guarda o espaço que vem depois do valor, e o Spring Boot 4 não o perdoa em todo lugar. Um
 * {@code spring.sql.init.mode=never } com espaço no fim impediu o perfil prd de subir ("No enum constant
 * DatabaseInitializationMode.NEVER "): a condição da autoconfiguração faz {@code valueOf} na string crua, sem passar
 * pelo Binder, que aparava. Nenhum teste sobe o prd (ele precisa do Oracle), então o defeito só apareceu rodando o jar.
 *
 * <p>O teste lê cada {@code application*.properties} do classpath com {@link Properties}, que guarda o fim do valor
 * como o Spring guarda, e recusa qualquer valor que termine em espaço ou tab.
 */
class PropertyValuesWhitespaceTest {

    @Test
    void noPropertyValueEndsWithWhitespace() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:application*.properties");
        List<String> names = new ArrayList<>();
        List<String> offenders = new ArrayList<>();
        for (Resource file : files) {
            names.add(file.getFilename());
            Properties properties = new Properties();
            try (InputStream in = file.getInputStream()) {
                properties.load(in);
            }
            properties.forEach((key, value) -> {
                String text = value.toString();
                if (!text.equals(text.stripTrailing())) {
                    offenders.add(file.getFilename() + ": " + key + "=[" + text + "]");
                }
            });
        }

        // Sem esta guarda, um padrão que não achasse os arquivos de main passaria sem olhar nada
        assertTrue(names.containsAll(List.of("application.properties", "application-prd.properties")),
                "application*.properties of src/main/resources not found on the classpath: " + names);
        assertEquals(List.of(), offenders, "Property values ending with whitespace");
    }
}
