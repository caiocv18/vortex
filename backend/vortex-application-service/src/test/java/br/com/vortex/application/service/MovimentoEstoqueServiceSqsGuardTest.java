package br.com.vortex.application.service;

import br.com.vortex.application.repository.MovimentoEstoqueRepository;
import br.com.vortex.application.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * O SQS virou opcional: o processamento assíncrono (que depende dele) precisa falhar na subida
 * quando ligado sem o SQS, em vez de falhar só no primeiro movimento.
 */
class MovimentoEstoqueServiceSqsGuardTest {

    private final ObjectProvider<SqsProducerService> noSqs =
            new StaticListableBeanFactory().getBeanProvider(SqsProducerService.class);

    @Test
    void asyncProcessingWithoutSqsFailsAtStartup() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> newService(true));

        assertTrue(failure.getMessage().contains("sqs.enabled=true"), failure.getMessage());
    }

    @Test
    void synchronousProcessingDoesNotNeedSqs() {
        assertDoesNotThrow(() -> newService(false));
    }

    private MovimentoEstoqueService newService(boolean processamentoAssincrono) {
        return new MovimentoEstoqueService(mock(MovimentoEstoqueRepository.class), mock(ProdutoRepository.class),
                noSqs, mock(MessageBrokerService.class), processamentoAssincrono);
    }
}
