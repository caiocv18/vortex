package br.com.vortex.application.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class OpenTelemetryLogbackConfigTest {

    @Test
    void attachesOneOpenTelemetryAppenderToTheRootLogger() {
        LoggerContext context = new LoggerContext();

        OpenTelemetryLogbackConfig.attachTo(context);
        OpenTelemetryLogbackConfig.attachTo(context);

        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        List<Object> appenders = new ArrayList<>();
        root.iteratorForAppenders().forEachRemaining(appenders::add);
        assertThat(appenders).singleElement().isInstanceOf(OpenTelemetryAppender.class);
        assertThat(root.getAppender(OpenTelemetryLogbackConfig.APPENDER_NAME).isStarted()).isTrue();
    }

    @Test
    void ignoresALoggerFactoryThatIsNotLogback() {
        assertThatNoException().isThrownBy(() -> OpenTelemetryLogbackConfig.attachTo(new NOPLoggerFactory()));
    }
}
