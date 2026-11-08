package br.com.vortex.authorization.service;

import br.com.vortex.authorization.config.MessagingConfig;
import br.com.vortex.authorization.event.UserLoggedOutEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Teste unitário (sem Quarkus) do flag auth.event.enabled e do envio só depois do commit, com um Emitter
 * e um registro de transação falsos: ligado e sem transação, o evento vai para o canal na hora; com
 * transação ativa, só no commit; no rollback, nunca. O caminho pelo CDI está no EventPublisherMessagingTest.
 */
class EventPublisherTest {

    private Emitter<String> emitter;
    private TransactionSynchronizationRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        emitter = mock(Emitter.class);
        when(emitter.send(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        registry = mock(TransactionSynchronizationRegistry.class);
        when(registry.getTransactionStatus()).thenReturn(Status.STATUS_NO_TRANSACTION);
    }

    @Test
    @DisplayName("Enabled: publishEvent sends the event JSON to user-events")
    void publishesWhenEnabled() {
        publisher(true).publishEvent(event());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(emitter).send(payload.capture());
        assertTrue(payload.getValue().contains("\"eventType\":\"user.logged_out\""), payload.getValue());
        assertTrue(payload.getValue().contains("\"userEmail\":\"user@test.com\""), payload.getValue());
    }

    @Test
    @DisplayName("Disabled: publishEvent does not touch the channel")
    void publishesNothingWhenDisabled() {
        publisher(false).publishEvent(event());

        verifyNoInteractions(emitter);
    }

    @Test
    @DisplayName("Enabled: publishEventSync sends and waits for the acknowledgement")
    void publishesSyncWhenEnabled() {
        publisher(true).publishEventSync(event());

        verify(emitter).send(anyString());
    }

    @Test
    @DisplayName("Disabled: publishEventSync does not touch the channel")
    void publishesNothingSyncWhenDisabled() {
        publisher(false).publishEventSync(event());

        verifyNoInteractions(emitter);
    }

    @Test
    @DisplayName("Inside an active transaction nothing is sent before the commit, and the commit sends it")
    void sendsOnlyAfterCommit() {
        when(registry.getTransactionStatus()).thenReturn(Status.STATUS_ACTIVE);

        publisher(true).publishEvent(event());
        verifyNoInteractions(emitter);

        Synchronization synchronization = registeredSynchronization();
        synchronization.beforeCompletion();
        verifyNoInteractions(emitter);

        synchronization.afterCompletion(Status.STATUS_COMMITTED);
        verify(emitter).send(anyString());
    }

    @Test
    @DisplayName("A transaction that rolls back never sends its event")
    void rollbackDiscardsTheEvent() {
        when(registry.getTransactionStatus()).thenReturn(Status.STATUS_ACTIVE);

        publisher(true).publishEvent(event());
        registeredSynchronization().afterCompletion(Status.STATUS_ROLLEDBACK);

        verifyNoInteractions(emitter);
    }

    @Test
    @DisplayName("A transaction already marked for rollback discards the event at once")
    void markedForRollbackDiscardsTheEvent() {
        when(registry.getTransactionStatus()).thenReturn(Status.STATUS_MARKED_ROLLBACK);

        publisher(true).publishEvent(event());

        verify(registry, never()).registerInterposedSynchronization(any());
        verifyNoInteractions(emitter);
    }

    @Test
    @DisplayName("publishEventSync inside a transaction also waits for the commit instead of sending early")
    void syncPublishInsideATransactionWaitsForTheCommit() {
        when(registry.getTransactionStatus()).thenReturn(Status.STATUS_ACTIVE);

        publisher(true).publishEventSync(event());
        verifyNoInteractions(emitter);

        registeredSynchronization().afterCompletion(Status.STATUS_COMMITTED);
        verify(emitter).send(anyString());
    }

    private Synchronization registeredSynchronization() {
        ArgumentCaptor<Synchronization> captor = ArgumentCaptor.forClass(Synchronization.class);
        verify(registry).registerInterposedSynchronization(captor.capture());
        return captor.getValue();
    }

    private EventPublisher publisher(boolean enabled) {
        EventPublisher publisher = new EventPublisher();
        publisher.objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher.messagingProperties = new MessagingConfig.MessagingProperties(enabled, "auth");
        publisher.transactionRegistry = registry;
        UserEventsChannel channel = new UserEventsChannel();
        channel.emitter = emitter;
        publisher.userEventsChannel = channel;
        return publisher;
    }

    private static UserLoggedOutEvent event() {
        return new UserLoggedOutEvent(UUID.randomUUID(), "user@test.com", "user",
            "session-1", "user_initiated", "127.0.0.1", "JUnit");
    }
}
