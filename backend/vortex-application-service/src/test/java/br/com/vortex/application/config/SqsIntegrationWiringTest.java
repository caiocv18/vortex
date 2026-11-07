package br.com.vortex.application.config;

import br.com.vortex.application.dto.MovimentoEstoqueDTO;
import br.com.vortex.application.model.enums.TipoMovimentacao;
import br.com.vortex.application.service.MovimentoEstoqueService;
import br.com.vortex.application.service.SqsConsumerService;
import br.com.vortex.application.service.SqsProducerService;
import io.awspring.cloud.sqs.listener.AbstractMessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import io.awspring.cloud.sqs.operations.SqsOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Com {@code sqs.enabled=true} o Spring Cloud AWS 4 monta cliente, template e os três
 * {@code @SqsListener}. Sem AWS: os listeners não iniciam e o envio vai para um mock.
 */
@SpringBootTest(properties = {
        "sqs.enabled=true",
        "sqs.processamento.assincrono.enabled=true",
        "spring.cloud.aws.sqs.listener.auto-startup=false",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key"})
@ActiveProfiles("test")
class SqsIntegrationWiringTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MessageListenerContainerRegistry listenerRegistry;

    @Autowired
    private MovimentoEstoqueService movimentoEstoqueService;

    @MockitoBean
    private SqsOperations sqsOperations;

    @Test
    void wiresClientProducerConsumerAndTheThreeListeners() {
        assertEquals(1, context.getBeanNamesForType(SqsAsyncClient.class).length);
        assertEquals(1, context.getBeanNamesForType(SqsProducerService.class).length);
        assertEquals(1, context.getBeanNamesForType(SqsConsumerService.class).length);

        Set<String> queues = listenerRegistry.getListenerContainers().stream()
                .map(container -> (AbstractMessageListenerContainer<?, ?, ?>) container)
                .flatMap(container -> container.getQueueNames().stream())
                .collect(Collectors.toSet());
        assertEquals(Set.of("vortex-movimento-estoque-queue", "vortex-notificacao-estoque-queue", "vortex-auditoria-queue"),
                queues);
        assertFalse(listenerRegistry.getListenerContainers().stream().anyMatch(MessageListenerContainer::isRunning));
    }

    @Test
    void asyncMovementIsSentToTheQueueWithItsAuditMessage() {
        MovimentoEstoqueDTO movimento = new MovimentoEstoqueDTO();
        movimento.setProdutoId(1L);
        movimento.setTipoMovimentacao(TipoMovimentacao.ENTRADA);
        movimento.setQuantidadeMovimentada(3);

        MovimentoEstoqueDTO resposta = movimentoEstoqueService.criar(movimento, "user-1");

        // Assíncrono: ainda sem ID, o movimento é gravado pelo consumidor da fila
        assertNull(resposta.getId());
        verify(sqsOperations, times(2)).send(any());
    }
}
