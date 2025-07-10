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

@ApplicationScoped
public class EventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventPublisher.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MessagingConfig.MessagingProperties messagingProperties;

    public void publishEvent(AuthEvent event) {
        if (!messagingProperties.eventEnabled) {
            LOGGER.debug("Event publishing disabled, skipping event: {} for user: {}", 
                        event.eventType, event.userEmail);
            return;
        }

        try {
            String eventJson = objectMapper.writeValueAsString(event);
            
            LOGGER.info("Would publish auth event: {} for user: {}", event.eventType, event.userEmail);
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
        if (!messagingProperties.eventEnabled) {
            LOGGER.debug("Event publishing disabled, skipping synchronous event: {} for user: {}", 
                        event.eventType, event.userEmail);
            return;
        }

        try {
            String eventJson = objectMapper.writeValueAsString(event);
            
            LOGGER.info("Would publish auth event synchronously: {} for user: {}", 
                       event.eventType, event.userEmail);
            
            LOGGER.debug("Event payload: {}", eventJson);
                
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize event: {} for user: {}", 
                        event.eventType, event.userEmail, e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error processing event synchronously: {} for user: {}", 
                        event.eventType, event.userEmail, e);
        }
    }
}

@ApplicationScoped
@IfBuildProfile("test")
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