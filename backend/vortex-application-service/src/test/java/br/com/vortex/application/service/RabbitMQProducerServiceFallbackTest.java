package br.com.vortex.application.service;

import br.com.vortex.application.dto.AuditoriaEventDTO;
import br.com.vortex.application.model.MovimentoEstoque;
import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.enums.TipoMovimentacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Com o fallback ligado, um evento que não pode ser montado (aqui, dado faltando que dá NullPointerException)
 * não derruba a operação de negócio: o erro fica no log e nada vai para o RabbitMQ.
 */
class RabbitMQProducerServiceFallbackTest {

    private static final String EXCHANGE = "vortex.exchange";

    private RabbitTemplate rabbitTemplate;
    private RabbitMQProducerService service;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        service = new RabbitMQProducerService(rabbitTemplate);
        ReflectionTestUtils.setField(service, "exchangeName", EXCHANGE);
        ReflectionTestUtils.setField(service, "rabbitMQEnabled", true);
        ReflectionTestUtils.setField(service, "fallbackEnabled", true);
    }

    @Test
    void productEventsWithoutProductTypeAreDroppedWithoutFailingTheOperation() {
        Produto semTipo = produtoSemTipo();

        assertDoesNotThrow(() -> service.publicarMovimentoEstoque(movimento(), semTipo, 10, "alice.estoque"));
        assertDoesNotThrow(() -> service.publicarProdutoCriado(semTipo, "alice.estoque"));
        assertDoesNotThrow(() -> service.publicarAlertaEstoqueBaixo(semTipo, 5, "alice.estoque"));

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void auditEventWithoutActionIsDroppedWithoutFailingTheOperation() {
        // A routing key usa acao.toLowerCase(): sem ação, a montagem falha antes do envio
        assertDoesNotThrow(() -> service.publicarAuditoria(null, "Produto", 7L, "detalhes", "alice.estoque",
                "SUCCESS", null));

        verify(rabbitTemplate, never()).convertAndSend(eq(EXCHANGE), anyString(), any(AuditoriaEventDTO.class));
    }

    private static Produto produtoSemTipo() {
        Produto produto = new Produto();
        produto.setId(7L);
        produto.setDescricao("Caneta azul");
        produto.setValorFornecedor(new BigDecimal("1.20"));
        produto.setQuantidadeEmEstoque(60);
        return produto;
    }

    private static MovimentoEstoque movimento() {
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setId(101L);
        movimento.setTipoMovimentacao(TipoMovimentacao.ENTRADA);
        movimento.setQuantidadeMovimentada(50);
        return movimento;
    }
}
