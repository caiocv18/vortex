package br.com.vortex.authorization.service;

import br.com.vortex.authorization.event.AuthEvent;
import br.com.vortex.authorization.config.MessagingConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Publica os eventos de autenticação no canal user-events.
 *
 * Os métodos de negócio do AuthService publicam de dentro da própria transação, antes do commit. Enviado
 * na hora, o evento saía mesmo quando a transação depois fazia rollback (um cadastro que falha na
 * unique constraint do e-mail anunciava um usuário que não existe) e podia chegar ao broker antes da
 * linha estar visível no banco. Por isso, com transação ativa, o envio fica para depois do commit e é
 * descartado no rollback; sem transação, sai na hora.
 */
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

    @Inject
    TransactionSynchronizationRegistry transactionRegistry;

    public void publishEvent(AuthEvent event) {
        if (!messagingProperties.isEventEnabled()) {
            LOGGER.debug("Event publishing disabled, skipping event: {} for user: {}",
                        event.eventType, event.userEmail);
            return;
        }

        String eventJson = serialize(event);
        if (eventJson != null && !deferUntilCommit(event, () -> send(event, eventJson))) {
            send(event, eventJson);
        }
    }

    /**
     * Envia e espera a confirmação do conector (até SYNC_ACK_TIMEOUT_SECONDS). Dentro de uma transação
     * não há o que esperar: o evento só pode sair depois do commit, que acontece quando o método de
     * negócio já retornou. Nesse caso ele sai depois do commit, sem espera, como no publishEvent.
     */
    public void publishEventSync(AuthEvent event) {
        if (!messagingProperties.isEventEnabled()) {
            LOGGER.debug("Event publishing disabled, skipping synchronous event: {} for user: {}",
                        event.eventType, event.userEmail);
            return;
        }

        String eventJson = serialize(event);
        if (eventJson == null || deferUntilCommit(event, () -> send(event, eventJson))) {
            return;
        }

        try {
            // Espera a confirmação do conector, com teto para não prender a thread de vez
            userEventsChannel.send(eventJson).toCompletableFuture().get(SYNC_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            LOGGER.info("Published auth event synchronously: {} for user: {}",
                       event.eventType, event.userEmail);

            LOGGER.debug("Event payload: {}", eventJson);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while publishing event: {} for user: {}",
                        event.eventType, event.userEmail, e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error processing event synchronously: {} for user: {}",
                        event.eventType, event.userEmail, e);
        }
    }

    /**
     * Com transação ativa, agenda o envio para depois do commit e devolve true. Se ela já está marcada
     * para rollback, nada do que gravou vai valer: descarta e também devolve true. Sem transação,
     * devolve false e quem chamou envia na hora.
     */
    private boolean deferUntilCommit(AuthEvent event, Runnable sendAfterCommit) {
        int status = transactionRegistry.getTransactionStatus();
        if (status == Status.STATUS_NO_TRANSACTION) {
            return false;
        }
        if (status != Status.STATUS_ACTIVE) {
            LOGGER.debug("Transaction will not commit (status {}), discarding auth event: {} for user: {}",
                        status, event.eventType, event.userEmail);
            return true;
        }
        transactionRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // nada: o evento depende do resultado do commit
            }

            @Override
            public void afterCompletion(int completionStatus) {
                if (completionStatus == Status.STATUS_COMMITTED) {
                    sendAfterCommit.run();
                } else {
                    LOGGER.debug("Transaction rolled back (status {}), discarding auth event: {} for user: {}",
                                completionStatus, event.eventType, event.userEmail);
                }
            }
        });
        return true;
    }

    private String serialize(AuthEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize event: {} for user: {}",
                        event.eventType, event.userEmail, e);
            return null;
        }
    }

    // Não espera o ack; falha do conector vira log de erro, nunca exceção para o fluxo de negócio
    private void send(AuthEvent event, String eventJson) {
        try {
            userEventsChannel.send(eventJson).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    LOGGER.error("Failed to publish auth event: {} for user: {}",
                                event.eventType, event.userEmail, failure);
                }
            });
            LOGGER.info("Published auth event: {} for user: {}", event.eventType, event.userEmail);
            LOGGER.debug("Event payload: {}", eventJson);
        } catch (Exception e) {
            LOGGER.error("Unexpected error processing event: {} for user: {}",
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
