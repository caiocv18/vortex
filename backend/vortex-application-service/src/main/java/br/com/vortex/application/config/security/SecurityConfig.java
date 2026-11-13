package br.com.vortex.application.config.security;

import br.com.vortex.application.config.DeploymentProfiles;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private UrlBasedCorsConfigurationSource corsConfigurationSource;

    @Autowired
    private Environment environment;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean cloud = DeploymentProfiles.isCloud(environment);
        Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> rules =
                cloud ? SecurityConfig::cloudRules : SecurityConfig::localRules;

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(rules);

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (!cloud) {
            // O console do H2 abre em frames da mesma origem; no cloud fica o DENY padrão
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        }

        return http.build();
    }

    /**
     * Demo pública: o {@code /health} e a API com token, mais nada. Swagger, {@code /v3/api-docs}, console do H2 e
     * {@code /api/test} já estão desligados pelo {@code application-cloud.properties} e pelo {@code @Profile} do
     * {@code TestController}; esta cadeia os nega de novo, para o caso de alguma dessas chaves mudar.
     */
    private static void cloudRules(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
            // O despacho de erro de um 403 (um VIEWER escrevendo) não leva o token e cairia no denyAll como 401
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers("/health").permitAll()
            .requestMatchers("/api/test/**").denyAll()
            .requestMatchers("/api/**").authenticated()
            .anyRequest().denyAll();
    }

    private static void localRules(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
            // Public endpoints
            .requestMatchers("/health", "/actuator/health").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
            .requestMatchers("/h2-console/**").permitAll()
            // Test endpoints
            .requestMatchers("/api/test/**").permitAll()
            // API endpoints that require authentication
            .requestMatchers("/api/**").authenticated()
            .anyRequest().permitAll();
    }
}
