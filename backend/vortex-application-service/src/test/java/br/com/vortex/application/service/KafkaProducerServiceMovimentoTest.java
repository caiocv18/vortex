package br.com.vortex.application.service;

import br.com.vortex.application.dto.MovimentoEstoqueEventDTO;
import br.com.vortex.application.model.MovimentoEstoque;
import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.TipoProduto;
import br.com.vortex.application.model.enums.TipoMovimentacao;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O evento que vai ao tópico {@code vortex.movimento.estoque} leva no {@code userId} o usuário que o serviço
 * recebeu: é a chave do join com os eventos do auth no pipeline de dados.
 */
class KafkaProducerServiceMovimentoTest {

    private static final String TOPIC = "vortex.movimento.estoque";

    @Test
    @SuppressWarnings("unchecked")
    void movementEventGoesToTheTopicWithTheUserId() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        KafkaProducerService service = new KafkaProducerService(kafkaTemplate);
        ReflectionTestUtils.setField(service, "kafkaEnabled", true);
        ReflectionTestUtils.setField(service, "movimentoEstoqueTopic", TOPIC);

        service.publicarMovimentoEstoque(movimento(), produto(), 10, "alice.estoque");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("produto-7"), event.capture());
        MovimentoEstoqueEventDTO sent = assertInstanceOf(MovimentoEstoqueEventDTO.class, event.getValue());
        assertEquals("alice.estoque", sent.getUserId());
        assertEquals("MOVIMENTO_ESTOQUE", sent.getEventType());
        assertEquals(101L, sent.getMovimentoId());
        assertEquals(10, sent.getEstoqueAnterior());
        assertEquals(60, sent.getEstoqueAtual());
    }

    private static Produto produto() {
        TipoProduto tipo = new TipoProduto();
        tipo.setId(1L);
        tipo.setNome("Papelaria");
        Produto produto = new Produto();
        produto.setId(7L);
        produto.setDescricao("Caneta azul");
        produto.setValorFornecedor(new BigDecimal("1.20"));
        produto.setQuantidadeEmEstoque(60);
        produto.setTipoProduto(tipo);
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
