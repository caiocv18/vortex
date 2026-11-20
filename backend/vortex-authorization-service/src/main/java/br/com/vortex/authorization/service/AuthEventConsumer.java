package br.com.vortex.authorization.service;

import br.com.vortex.authorization.event.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example event consumer for demonstration purposes.
 * In a real application, this would be in a separate service.
 * This shows how other services can react to authentication events.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class AuthEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthEventConsumer.class);

    private final ObjectMapper objectMapper;

    @Inject
    public AuthEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Incoming("user-events")
    public void handleUserEvent(String eventJson) {
        try {
            JsonNode eventNode = objectMapper.readTree(eventJson);
            String eventType = eventNode.get("eventType").asText();
            String userEmail = eventNode.get("userEmail").asText();
            
            LOGGER.info("Processing auth event: {} for user: {}", eventType, userEmail);
            
            switch (eventType) {
                case "user.created":
                    handleUserCreated(eventJson);
                    break;
                case "user.logged_in":
                    handleUserLoggedIn(eventJson);
                    break;
                case "user.logged_out":
                    handleUserLoggedOut(eventJson);
                    break;
                case "password.changed":
                    handlePasswordChanged(eventJson);
                    break;
                case "password.reset_requested":
                    handlePasswordResetRequested(eventJson);
                    break;
                case "email.verified":
                    handleEmailVerified(eventJson);
                    break;
                default:
                    LOGGER.warn("Unknown event type: {}", eventType);
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to process auth event: {}", eventJson, e);
        }
    }

    private void handleUserCreated(String eventJson) {
        try {
            UserCreatedEvent event = objectMapper.readValue(eventJson, UserCreatedEvent.class);
            
            LOGGER.info("User created: {} ({})", event.userEmail, event.username);
            LOGGER.debug("User roles: {}, verified: {}", event.roles, event.isVerified);
            // A real consumer would send the welcome email, create the user profile in
            // other services and set up the default preferences here.
        } catch (Exception e) {
            LOGGER.error("Failed to handle user created event", e);
        }
    }

    private void handleUserLoggedIn(String eventJson) {
        try {
            UserLoggedInEvent event = objectMapper.readValue(eventJson, UserLoggedInEvent.class);
            
            LOGGER.info("User logged in: {} from IP: {}", event.userEmail, event.ipAddress);
            LOGGER.debug("Login method: {}, session: {}", event.loginMethod, event.sessionId);
            // A real consumer would update the login analytics, look for suspicious login
            // patterns and alert the user when the login comes from a new device.
        } catch (Exception e) {
            LOGGER.error("Failed to handle user logged in event", e);
        }
    }

    private void handleUserLoggedOut(String eventJson) {
        try {
            UserLoggedOutEvent event = objectMapper.readValue(eventJson, UserLoggedOutEvent.class);
            
            LOGGER.info("User logged out: {} - reason: {}", event.userEmail, event.logoutReason);
            // A real consumer would clean up the user's sessions and update the analytics here.
        } catch (Exception e) {
            LOGGER.error("Failed to handle user logged out event", e);
        }
    }

    private void handlePasswordChanged(String eventJson) {
        try {
            PasswordChangedEvent event = objectMapper.readValue(eventJson, PasswordChangedEvent.class);
            
            LOGGER.info("Password changed for user: {} - reason: {}", event.userEmail, event.changeReason);
            // A real consumer would notify the user, record the security event and revoke
            // every open session when the change was caused by a security breach.
        } catch (Exception e) {
            LOGGER.error("Failed to handle password changed event", e);
        }
    }

    private void handlePasswordResetRequested(String eventJson) {
        try {
            PasswordResetRequestedEvent event = objectMapper.readValue(eventJson, PasswordResetRequestedEvent.class);
            
            LOGGER.info("Password reset requested for user: {}", event.userEmail);
            // A real consumer would send the reset email, record the security event and look
            // for suspicious reset patterns here.
        } catch (Exception e) {
            LOGGER.error("Failed to handle password reset requested event", e);
        }
    }

    private void handleEmailVerified(String eventJson) {
        try {
            EmailVerifiedEvent event = objectMapper.readValue(eventJson, EmailVerifiedEvent.class);
            
            LOGGER.info("Email verified for user: {}", event.userEmail);
            // A real consumer would send the welcome email, enable the full account features
            // and turn on the e-mail notifications here.
        } catch (Exception e) {
            LOGGER.error("Failed to handle email verified event", e);
        }
    }
}