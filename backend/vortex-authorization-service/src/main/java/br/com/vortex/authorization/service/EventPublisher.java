package br.com.vortex.authorization.service;

import br.com.vortex.authorization.event.AuthEvent;
import br.com.vortex.authorization.config.MessagingConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class EventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventPublisher.class);

    // Quanto o publishEventSync espera a confirmação do conector antes de desistir
    static final long SYNC_ACK_TIMEOUT_SECONDS = 10;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MessagingConfig.MessagingProperties messagingProperties;

    // Só é chamado com auth.event.enabled=true (ver UserEventsChannel). Ligar os eventos sem um
    // perfil que dê conector ao canal (kafka ou rabbitmq) faz cada envio virar log de erro.
    @Inject
    UserEventsChannel userEventsChannel;

    public void publishEvent(AuthEvent event) {
        if (!messagingProperties.isEventEnabled()) {
            LOGGER.debug("Event publishing disabled, skipping event: {} for user: {}",
                        event.eventType, event.userEmail);
            return;
        }

        try {
            String eventJson = objectMapper.writeValueAsString(event);

            userEventsChannel.send(eventJson).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    LOGGER.error("Failed to publish auth event: {} for user: {}",
                                event.eventType, event.userEmail, failure);
                }
            });
            LOGGER.info("Published auth event: {} for user: {}", event.eventType, event.userEmail);
            LOGGER.debug("Event payload: {}", eventJson);

        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize event: {} for user: {}",
                        event.eventType, event.userEmail, e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error processing event: {} for user: {}",
                        event.eventType, event.userEmail, e);
        }
    }

    public void publishEventSync(AuthEvent event) {
        if (!messagingProperties.isEventEnabled()) {
            LOGGER.debug("Event publishing disabled, skipping synchronous event: {} for user: {}",
                        event.eventType, event.userEmail);
            return;
        }

        try {
            String eventJson = objectMapper.writeValueAsString(event);

            // Espera a confirmação do conector, com teto para não prender a thread de vez
            userEventsChannel.send(eventJson).toCompletableFuture().get(SYNC_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            LOGGER.info("Published auth event synchronously: {} for user: {}",
                       event.eventType, event.userEmail);

            LOGGER.debug("Event payload: {}", eventJson);

        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize event: {} for user: {}",
                        event.eventType, event.userEmail, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while publishing event: {} for user: {}",
                        event.eventType, event.userEmail, e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error processing event synchronously: {} for user: {}",
                        event.eventType, event.userEmail, e);
        }
    }
}

@ApplicationScoped
@IfBuildProfile("test")
@jakarta.enterprise.inject.Alternative
class MockEventPublisher extends EventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockEventPublisher.class);

    @Override
    public void publishEvent(AuthEvent event) {
        LOGGER.info("Mock: Publishing auth event: {} for user: {}", event.eventType, event.userEmail);
    }

    @Override
    public void publishEventSync(AuthEvent event) {
        LOGGER.info("Mock: Publishing auth event synchronously: {} for user: {}",
                   event.eventType, event.userEmail);
    }
}
