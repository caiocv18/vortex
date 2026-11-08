package br.com.vortex.authorization.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MessagingConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessagingConfig.class);

    // Mesmo padrão do application.properties (AUTH_EVENT_ENABLED, desligado)
    @ConfigProperty(name = "auth.event.enabled", defaultValue = "false")
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

    /**
     * Bean @ApplicationScoped: quem o injeta recebe um client proxy do CDI. Os valores só podem
     * ser lidos por método, que o proxy repassa à instância real. Com os antigos campos públicos,
     * a leitura caía no próprio proxy, que nunca é inicializado: eventEnabled era sempre false
     * e a publicação de eventos não ligava nem com auth.event.enabled=true.
     */
    public static class MessagingProperties {
        private boolean eventEnabled;
        private String routingKey;

        // Exigido pelo client proxy do CDI
        protected MessagingProperties() {
        }

        public MessagingProperties(boolean eventEnabled, String routingKey) {
            this.eventEnabled = eventEnabled;
            this.routingKey = routingKey;
        }

        public boolean isEventEnabled() {
            return eventEnabled;
        }

        public String getRoutingKey() {
            return routingKey;
        }
    }
}
