package br.com.vortex.authorization.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MessagingConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessagingConfig.class);

    @ConfigProperty(name = "auth.event.enabled", defaultValue = "true")
    boolean eventEnabled;

    @ConfigProperty(name = "auth.event.routing-key", defaultValue = "auth")
    String routingKey;

    @Produces
    @ApplicationScoped
    public MessagingProperties messagingProperties() {
        LOGGER.info("Messaging configuration - Events enabled: {}, Routing key: {}", 
                   eventEnabled, routingKey);
        return new MessagingProperties(eventEnabled, routingKey);
    }

    public static class MessagingProperties {
        public final boolean eventEnabled;
        public final String routingKey;

        public MessagingProperties(boolean eventEnabled, String routingKey) {
            this.eventEnabled = eventEnabled;
            this.routingKey = routingKey;
        }
    }
}