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

    @Inject
    ObjectMapper objectMapper;

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
            
            // Example: Send welcome email
            // emailService.sendWelcomeEmail(event.userEmail, event.username);
            
            // Example: Create user profile in other services
            // profileService.createUserProfile(event.userId, event.username, event.userEmail);
            
            // Example: Setup default preferences
            // preferencesService.createDefaultPreferences(event.userId);
            
        } catch (Exception e) {
            LOGGER.error("Failed to handle user created event", e);
        }
    }

    private void handleUserLoggedIn(String eventJson) {
        try {
            UserLoggedInEvent event = objectMapper.readValue(eventJson, UserLoggedInEvent.class);
            
            LOGGER.info("User logged in: {} from IP: {}", event.userEmail, event.ipAddress);
            LOGGER.debug("Login method: {}, session: {}", event.loginMethod, event.sessionId);
            
            // Example: Update last login analytics
            // analyticsService.recordLogin(event.userId, event.timestamp, event.ipAddress);
            
            // Example: Check for suspicious login patterns
            // securityService.checkLoginPattern(event.userId, event.ipAddress, event.userAgent);
            
            // Example: Send login notification if from new device
            // if (securityService.isNewDevice(event.userId, event.userAgent)) {
            //     notificationService.sendLoginAlert(event.userEmail, event.ipAddress);
            // }
            
        } catch (Exception e) {
            LOGGER.error("Failed to handle user logged in event", e);
        }
    }

    private void handleUserLoggedOut(String eventJson) {
        try {
            UserLoggedOutEvent event = objectMapper.readValue(eventJson, UserLoggedOutEvent.class);
            
            LOGGER.info("User logged out: {} - reason: {}", event.userEmail, event.logoutReason);
            
            // Example: Clean up user sessions
            // sessionService.cleanupUserSession(event.userId, event.sessionId);
            
            // Example: Update analytics
            // analyticsService.recordLogout(event.userId, event.timestamp, event.logoutReason);
            
        } catch (Exception e) {
            LOGGER.error("Failed to handle user logged out event", e);
        }
    }

    private void handlePasswordChanged(String eventJson) {
        try {
            PasswordChangedEvent event = objectMapper.readValue(eventJson, PasswordChangedEvent.class);
            
            LOGGER.info("Password changed for user: {} - reason: {}", event.userEmail, event.changeReason);
            
            // Example: Send security notification
            // emailService.sendPasswordChangedNotification(event.userEmail, event.changeReason);
            
            // Example: Log security event
            // securityService.logPasswordChange(event.userId, event.changeReason, event.ipAddress);
            
            // Example: Revoke all existing sessions if password was compromised
            // if ("security_breach".equals(event.changeReason)) {
            //     sessionService.revokeAllUserSessions(event.userId);
            // }
            
        } catch (Exception e) {
            LOGGER.error("Failed to handle password changed event", e);
        }
    }

    private void handlePasswordResetRequested(String eventJson) {
        try {
            PasswordResetRequestedEvent event = objectMapper.readValue(eventJson, PasswordResetRequestedEvent.class);
            
            LOGGER.info("Password reset requested for user: {}", event.userEmail);
            
            // Example: Send reset email
            // emailService.sendPasswordResetEmail(event.userEmail, event.resetTokenId.toString());
            
            // Example: Log security event
            // securityService.logPasswordResetRequest(event.userId, event.ipAddress);
            
            // Example: Check for suspicious reset patterns
            // securityService.checkResetPattern(event.userId, event.ipAddress);
            
        } catch (Exception e) {
            LOGGER.error("Failed to handle password reset requested event", e);
        }
    }

    private void handleEmailVerified(String eventJson) {
        try {
            EmailVerifiedEvent event = objectMapper.readValue(eventJson, EmailVerifiedEvent.class);
            
            LOGGER.info("Email verified for user: {}", event.userEmail);
            
            // Example: Send welcome email after verification
            // emailService.sendWelcomeEmail(event.userEmail, event.username);
            
            // Example: Enable full account features
            // accountService.enableFullFeatures(event.userId);
            
            // Example: Update user preferences
            // preferencesService.enableEmailNotifications(event.userId);
            
        } catch (Exception e) {
            LOGGER.error("Failed to handle email verified event", e);
        }
    }
}