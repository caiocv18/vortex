package br.com.vortex.application.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Para o boot quando a senha do banco aponta para uma variável que não existe, como {@code ${VORTEX_DB_PASSWORD}}
 * sem o export no perfil {@code postgres}.
 *
 * <p>O {@link SecurityConfigurationValidator} chega tarde para isso: é um {@code ApplicationRunner}, que só roda com o
 * contexto pronto, quando o Flyway já conectou. E o Binder do Spring Boot não reclama do placeholder: ele entrega o
 * texto {@code ${VORTEX_DB_PASSWORD}} ao DataSource como se fosse a senha, e o PostgreSQL responde com uma falha de
 * autenticação que não cita a variável (medido no Spring Boot 4.1.2). Um pós-processador da fábrica de beans roda
 * antes de qualquer bean ser criado, o DataSource incluído, e o {@code Environment} resolve o valor no modo estrito,
 * que lança com o nome da variável.
 *
 * <p>Ele nasce de um método {@code static}, com o {@code Environment} no parâmetro. Num {@code @Component} a injeção
 * pelo construtor falha: os pós-processadores da fábrica são criados antes do {@code AutowiredAnnotationBeanPostProcessor},
 * que é quem escolhe o construtor, e o Spring procura um construtor vazio (medido). Argumento de método-fábrica é
 * resolvido sem ele, e o {@code static} evita criar esta classe de configuração antes da hora.
 */
@Configuration(proxyBeanMethods = false)
public class DatasourcePasswordCheck {

    static final String PASSWORD_KEY = "spring.datasource.password";

    @Bean
    static BeanFactoryPostProcessor failFastOnUnresolvedDatasourcePassword(Environment environment) {
        return beanFactory -> check(environment);
    }

    static void check(Environment environment) {
        try {
            environment.getProperty(PASSWORD_KEY);
        } catch (IllegalArgumentException unresolved) {
            throw new IllegalStateException(PASSWORD_KEY + " is not set: " + unresolved.getMessage(), unresolved);
        }
    }
}
