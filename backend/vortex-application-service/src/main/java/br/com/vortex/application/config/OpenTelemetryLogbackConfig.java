package br.com.vortex.application.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Leva o log do Logback ao OpenTelemetry (e dali ao OTLP, quando {@code VORTEX_OTEL_ENABLED=true}). O Boot 4.1
 * exporta o log por OTLP, mas não instala o appender do Logback: sem este bean nenhum log chega ao Loki.
 *
 * <p>O appender é posto no logger raiz por código, e não num {@code logback-spring.xml}: assim a configuração
 * programática do Boot continua valendo, inclusive o {@code logging.structured.format.console} do perfil cloud.
 * Cada registro leva o trace e o span correntes, e é isso que liga o log do Loki ao trace do Tempo.
 */
@Configuration(proxyBeanMethods = false)
public class OpenTelemetryLogbackConfig {

    static final String APPENDER_NAME = "OTEL";

    @Bean
    InitializingBean openTelemetryAppenderInitializer(OpenTelemetry openTelemetry) {
        return () -> {
            attachTo(LoggerFactory.getILoggerFactory());
            OpenTelemetryAppender.install(openTelemetry);
        };
    }

    /** Põe o appender no logger raiz uma vez só; fora do Logback não faz nada. */
    static void attachTo(ILoggerFactory loggerFactory) {
        if (!(loggerFactory instanceof LoggerContext context)) {
            return;
        }
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root.getAppender(APPENDER_NAME) != null) {
            return;
        }
        OpenTelemetryAppender appender = new OpenTelemetryAppender();
        appender.setName(APPENDER_NAME);
        appender.setContext(context);
        appender.start();
        root.addAppender(appender);
    }
}
