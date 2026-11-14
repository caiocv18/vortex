package br.com.vortex.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * A única configuração de CORS da aplicação, aplicada pelo filtro da cadeia de segurança ({@code SecurityConfig}).
 *
 * <p>As origens vêm de {@code vortex.cors.allowed-origins}: os frontends locais na base, e
 * {@code VORTEX_CLOUD_CORS_ORIGINS} no perfil {@code cloud}. Com a lista vazia nada é registrado, e o navegador
 * recusa toda chamada de outra origem.
 *
 * <p>O retorno declara o tipo concreto porque o Spring MVC também publica um {@code CorsConfigurationSource}
 * (o {@code HandlerMappingIntrospector}); por {@code UrlBasedCorsConfigurationSource} este bean é único.
 */
@Configuration
public class CorsConfig {

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${vortex.cors.allowed-origins:}") String[] allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (origins.isEmpty()) {
            return source;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        // Sem credenciais: a API só aceita o token no cabeçalho Authorization (Bearer) e nenhum serviço usa
        // cookie. Sem credencial ambiente não há CSRF, que é o motivo do dismiss do alerta do CodeQL
        // (docs/security/alert-triage.md); um cookie de sessão no futuro exige rever as duas coisas juntas.
        configuration.setAllowCredentials(false);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
