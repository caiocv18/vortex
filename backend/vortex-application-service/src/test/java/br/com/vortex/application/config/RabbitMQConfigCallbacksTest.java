package br.com.vortex.application.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Os callbacks de confirmação e de retorno do RabbitTemplate registram a falha no log da aplicação
 * (antes iam para o System.err, fora do Logback e do OpenTelemetry).
 */
class RabbitMQConfigCallbacksTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RabbitMQConfig.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private RabbitTemplate template;

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
        template = new RabbitMQConfig().rabbitTemplate(mock(ConnectionFactory.class));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void nackIsLoggedAsError() {
        RabbitTemplate.ConfirmCallback callback =
                (RabbitTemplate.ConfirmCallback) ReflectionTestUtils.getField(template, "confirmCallback");

        callback.confirm(null, false, "canal fechado");
        callback.confirm(null, true, null);

        assertEquals(1, appender.list.size());
        assertEquals(Level.ERROR, appender.list.get(0).getLevel());
        assertEquals("Falha ao enviar mensagem: canal fechado", appender.list.get(0).getFormattedMessage());
    }

    @Test
    void returnedMessageIsLoggedAsWarning() {
        RabbitTemplate.ReturnsCallback callback =
                (RabbitTemplate.ReturnsCallback) ReflectionTestUtils.getField(template, "returnsCallback");
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8));

        callback.returnedMessage(new ReturnedMessage(message, 312, "NO_ROUTE", "vortex.exchange", "sem.fila"));

        assertEquals(1, appender.list.size());
        assertEquals(Level.WARN, appender.list.get(0).getLevel());
        assertTrue(appender.list.get(0).getFormattedMessage().startsWith("Mensagem retornada: "),
                appender.list.get(0).getFormattedMessage());
    }
}
