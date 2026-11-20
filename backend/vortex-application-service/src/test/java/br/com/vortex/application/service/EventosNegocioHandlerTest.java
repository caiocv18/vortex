package br.com.vortex.application.service;

import br.com.vortex.application.dto.AlertaEstoqueEventDTO;
import br.com.vortex.application.dto.AuditoriaEventDTO;
import br.com.vortex.application.dto.MovimentoEstoqueEventDTO;
import br.com.vortex.application.dto.ProdutoDTO;
import br.com.vortex.application.dto.ProdutoEventDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventosNegocioHandlerTest {

    private final NotificacaoService notificacao = mock(NotificacaoService.class);
    private final AnalyticsService analytics = mock(AnalyticsService.class);
    private final IntegracaoExternaService integracao = mock(IntegracaoExternaService.class);
    private final EventosNegocioHandler handler = new EventosNegocioHandler(notificacao, analytics, integracao);

    @ParameterizedTest
    @ValueSource(ints = {50, 5, 0})
    void movementFeedsAnalyticsIntegrationAndDashboard(int estoqueAtual) {
        MovimentoEstoqueEventDTO event = new MovimentoEstoqueEventDTO();
        event.setProdutoId(1L);
        event.setEstoqueAtual(estoqueAtual);

        handler.movimentoEstoque(event);

        verify(analytics).processarMovimentoEstoque(event);
        verify(integracao).sincronizarMovimentoEstoque(event);
        verify(notificacao).atualizarDashboard(event);
    }

    @Test
    void createdAndDeletedProductsReachTheExternalSystems() {
        ProdutoEventDTO criado = produto("CREATED");
        ProdutoEventDTO excluido = produto("DELETED");

        handler.eventoProduto(criado);
        handler.eventoProduto(excluido);

        verify(integracao).sincronizarProdutoExterno(criado);
        verify(notificacao).notificarNovoProduto(criado);
        verify(integracao).removerProdutoExterno(excluido);
        verify(notificacao).notificarProdutoDescontinuado(excluido);
    }

    @Test
    void anUpdateNotifiesOnlyARealPriceChange() {
        ProdutoEventDTO mesmoPreco = produto("UPDATED");
        mesmoPreco.setValorFornecedor(new BigDecimal("10.0"));
        mesmoPreco.setDadosAnteriores(anterior("10.00"));
        ProdutoEventDTO novoPreco = produto("UPDATED");
        novoPreco.setValorFornecedor(new BigDecimal("12.00"));
        novoPreco.setDadosAnteriores(anterior("10.00"));

        handler.eventoProduto(mesmoPreco);
        handler.eventoProduto(novoPreco);

        verify(integracao).atualizarProdutoExterno(mesmoPreco);
        verify(notificacao, never()).notificarMudancaPreco(mesmoPreco);
        verify(notificacao).notificarMudancaPreco(novoPreco);
    }

    @Test
    void anUnknownProductActionIsIgnored() {
        handler.eventoProduto(produto("ARCHIVED"));

        verifyNoInteractions(integracao, notificacao);
    }

    @Test
    void aCriticalAlertOrdersRestockAndAnImmediateOneOpensATicket() {
        AlertaEstoqueEventDTO critico = new AlertaEstoqueEventDTO();
        critico.setPrioridade("CRITICAL");
        AlertaEstoqueEventDTO imediato = new AlertaEstoqueEventDTO();
        imediato.setAcaoImediata(true);
        AlertaEstoqueEventDTO semFlag = new AlertaEstoqueEventDTO();

        handler.alertaEstoque(critico);
        handler.alertaEstoque(imediato);
        handler.alertaEstoque(semFlag);

        verify(integracao).criarPedidoReposicaoAutomatico(critico);
        verify(integracao, never()).criarTicketUrgente(critico);
        verify(integracao).criarPedidoReposicaoAutomatico(imediato);
        verify(integracao).criarTicketUrgente(imediato);
        verify(integracao, never()).criarPedidoReposicaoAutomatico(semFlag);
        verify(notificacao).enviarAlertaEstoque(semFlag);
        verify(notificacao).atualizarDashboardAlertas(semFlag);
    }

    @Test
    void aSuspiciousAuditRaisesASecurityAlert() {
        AuditoriaEventDTO suspeita = new AuditoriaEventDTO();
        AuditoriaEventDTO comum = new AuditoriaEventDTO();
        when(analytics.isOperacaoSuspeita(suspeita)).thenReturn(true);

        handler.auditoria(suspeita);
        handler.auditoria(comum);

        verify(notificacao).enviarAlertaSeguranca(suspeita);
        verify(notificacao, never()).enviarAlertaSeguranca(comum);
        verify(analytics).registrarAuditoria(comum);
        verify(analytics).atualizarMetricasCompliance(comum);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
        "10.0, 10.00, false",
        "10.00, 9.99, true",
        "10.00, null, true"
    })
    void priceChangeComparesTheValueNotTheScale(BigDecimal atual, BigDecimal anterior, boolean mudou) {
        assertEquals(mudou, EventosNegocioHandler.mudouValor(atual, anterior));
    }

    private static ProdutoEventDTO produto(String action) {
        ProdutoEventDTO event = new ProdutoEventDTO();
        event.setAction(action);
        event.setProdutoId(7L);
        return event;
    }

    private static ProdutoDTO anterior(String valor) {
        ProdutoDTO dto = new ProdutoDTO();
        dto.setValorFornecedor(new BigDecimal(valor));
        return dto;
    }
}
