package br.com.vortex.application.service;

import br.com.vortex.application.dto.AlertaEstoqueEventDTO;
import br.com.vortex.application.dto.AuditoriaEventDTO;
import br.com.vortex.application.dto.MovimentoEstoqueEventDTO;
import br.com.vortex.application.dto.ProdutoEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * O que fazer com cada evento de negócio, igual para o Kafka e o RabbitMQ. Os consumidores só recebem a
 * mensagem, chamam este handler e confirmam (ou rejeitam) no protocolo de cada um.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventosNegocioHandler {

    private final NotificacaoService notificacaoService;

    private final AnalyticsService analyticsService;

    private final IntegracaoExternaService integracaoExternaService;

    public void movimentoEstoque(MovimentoEstoqueEventDTO event) {
        // 1. Atualizar analytics em tempo real
        analyticsService.processarMovimentoEstoque(event);

        // 2. Verificar se precisa gerar alertas
        verificarAlertas(event);

        // 3. Integrar com sistemas externos (ERP, WMS, etc.)
        integracaoExternaService.sincronizarMovimentoEstoque(event);

        // 4. Atualizar dashboards em tempo real
        notificacaoService.atualizarDashboard(event);
    }

    public void eventoProduto(ProdutoEventDTO event) {
        switch (event.getAction()) {
            case "CREATED" -> produtoCriado(event);
            case "UPDATED" -> produtoAtualizado(event);
            case "DELETED" -> produtoExcluido(event);
            default -> log.warn("Ação de produto desconhecida, evento ignorado: {}", event.getAction());
        }
    }

    public void alertaEstoque(AlertaEstoqueEventDTO event) {
        boolean acaoImediata = Boolean.TRUE.equals(event.getAcaoImediata());

        // 1. Enviar notificações por email/SMS
        notificacaoService.enviarAlertaEstoque(event);

        // 2. Atualizar dashboard com alertas
        notificacaoService.atualizarDashboardAlertas(event);

        // 3. Se for crítico, integrar com sistema de compras
        if ("CRITICAL".equals(event.getPrioridade()) || acaoImediata) {
            integracaoExternaService.criarPedidoReposicaoAutomatico(event);
        }

        // 4. Registrar no sistema de tickets (se necessário)
        if (acaoImediata) {
            integracaoExternaService.criarTicketUrgente(event);
        }
    }

    public void auditoria(AuditoriaEventDTO event) {
        // 1. Armazenar em sistema de auditoria
        analyticsService.registrarAuditoria(event);

        // 2. Verificar se é uma operação suspeita
        if (analyticsService.isOperacaoSuspeita(event)) {
            notificacaoService.enviarAlertaSeguranca(event);
        }

        // 3. Atualizar métricas de compliance
        analyticsService.atualizarMetricasCompliance(event);
    }

    /** Compara por valor (compareTo), não por escala: 10.0 e 10.00 são o mesmo preço. */
    static boolean mudouValor(BigDecimal atual, BigDecimal anterior) {
        return anterior == null || atual.compareTo(anterior) != 0;
    }

    private void verificarAlertas(MovimentoEstoqueEventDTO event) {
        // Verificar se o estoque está baixo após a movimentação
        if (event.getEstoqueAtual() <= 10 && event.getEstoqueAtual() > 0) {
            log.warn("Estoque baixo detectado após movimentação - Produto: {}, Quantidade: {}",
                event.getProdutoId(), event.getEstoqueAtual());
        } else if (event.getEstoqueAtual() <= 0) {
            log.error("Produto esgotado após movimentação - Produto: {}", event.getProdutoId());
        }
    }

    private void produtoCriado(ProdutoEventDTO event) {
        // Integrar com catálogo de produtos externo
        integracaoExternaService.sincronizarProdutoExterno(event);

        // Notificar equipe de compras sobre novo produto
        notificacaoService.notificarNovoProduto(event);
    }

    private void produtoAtualizado(ProdutoEventDTO event) {
        // Sincronizar mudanças com sistemas externos
        integracaoExternaService.atualizarProdutoExterno(event);

        // Se houve mudança significativa no preço, notificar
        if (event.getDadosAnteriores() != null
                && mudouValor(event.getValorFornecedor(), event.getDadosAnteriores().getValorFornecedor())) {
            notificacaoService.notificarMudancaPreco(event);
        }
    }

    private void produtoExcluido(ProdutoEventDTO event) {
        // Remover de sistemas externos
        integracaoExternaService.removerProdutoExterno(event);

        // Notificar sobre descontinuação
        notificacaoService.notificarProdutoDescontinuado(event);
    }
}
