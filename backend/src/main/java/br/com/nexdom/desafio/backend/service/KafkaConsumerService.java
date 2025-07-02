package br.com.nexdom.desafio.backend.service;

import br.com.nexdom.desafio.backend.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Serviço responsável por consumir eventos do Apache Kafka.
 * 
 * Este serviço processa:
 * - Eventos de movimentação de estoque para analytics
 * - Alertas de estoque para notificações
 * - Eventos de auditoria para compliance
 * - Integração com sistemas externos
 */
@Slf4j
@Service
public class KafkaConsumerService {

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private IntegracaoExternaService integracaoExternaService;

    // ================================
    // CONSUMER DE MOVIMENTOS DE ESTOQUE
    // ================================

    @KafkaListener(topics = "nexdom.movimento.estoque", groupId = "nexdom-movimento-group")
    public void processarMovimentoEstoque(@Payload MovimentoEstoqueEventDTO event,
                                        @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                        @Header(KafkaHeaders.OFFSET) long offset,
                                        Acknowledgment acknowledgment) {
        try {
            log.info("Processando evento de movimento de estoque - Produto: {}, Tipo: {}, Quantidade: {}", 
                event.getProdutoId(), event.getTipoMovimentacao(), event.getQuantidadeMovimentada());

            // 1. Atualizar analytics em tempo real
            analyticsService.processarMovimentoEstoque(event);

            // 2. Verificar se precisa gerar alertas
            verificarAlertas(event);

            // 3. Integrar com sistemas externos (ERP, WMS, etc.)
            integracaoExternaService.sincronizarMovimentoEstoque(event);

            // 4. Atualizar dashboards em tempo real
            notificacaoService.atualizarDashboard(event);

            acknowledgment.acknowledge();
            log.debug("Evento de movimento processado com sucesso - Key: {}, Offset: {}", key, offset);

        } catch (Exception e) {
            log.error("Erro ao processar evento de movimento de estoque - Key: {}, Offset: {}", key, offset, e);
            // Em caso de erro, não fazer acknowledge para reprocessar
        }
    }

    // ================================
    // CONSUMER DE EVENTOS DE PRODUTO
    // ================================

    @KafkaListener(topics = "nexdom.produto.events", groupId = "nexdom-produto-group")
    public void processarEventoProduto(@Payload ProdutoEventDTO event,
                                     @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                     Acknowledgment acknowledgment) {
        try {
            log.info("Processando evento de produto - Ação: {}, Produto: {}", 
                event.getAction(), event.getProdutoId());

            switch (event.getAction()) {
                case "CREATED":
                    processarProdutoCriado(event);
                    break;
                case "UPDATED":
                    processarProdutoAtualizado(event);
                    break;
                case "DELETED":
                    processarProdutoExcluido(event);
                    break;
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Erro ao processar evento de produto - Key: {}", key, e);
        }
    }

    // ================================
    // CONSUMER DE ALERTAS DE ESTOQUE
    // ================================

    @KafkaListener(topics = "nexdom.alertas.estoque", groupId = "nexdom-alertas-group")
    public void processarAlertaEstoque(@Payload AlertaEstoqueEventDTO event,
                                     @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                     Acknowledgment acknowledgment) {
        try {
            log.warn("Processando alerta de estoque - Tipo: {}, Produto: {}, Quantidade: {}", 
                event.getTipoAlerta(), event.getProdutoId(), event.getQuantidadeAtual());

            // 1. Enviar notificações por email/SMS
            notificacaoService.enviarAlertaEstoque(event);

            // 2. Atualizar dashboard com alertas
            notificacaoService.atualizarDashboardAlertas(event);

            // 3. Se for crítico, integrar com sistema de compras
            if ("CRITICAL".equals(event.getPrioridade()) || event.getAcaoImediata()) {
                integracaoExternaService.criarPedidoReposicaoAutomatico(event);
            }

            // 4. Registrar no sistema de tickets (se necessário)
            if (event.getAcaoImediata()) {
                integracaoExternaService.criarTicketUrgente(event);
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Erro ao processar alerta de estoque - Key: {}", key, e);
        }
    }

    // ================================
    // CONSUMER DE AUDITORIA
    // ================================

    @KafkaListener(topics = "nexdom.auditoria", groupId = "nexdom-auditoria-group")
    public void processarAuditoria(@Payload AuditoriaEventDTO event,
                                 @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                 Acknowledgment acknowledgment) {
        try {
            log.debug("Processando evento de auditoria - Ação: {}, Entidade: {}", 
                event.getAcao(), event.getEntidade());

            // 1. Armazenar em sistema de auditoria
            analyticsService.registrarAuditoria(event);

            // 2. Verificar se é uma operação suspeita
            if (analyticsService.isOperacaoSuspeita(event)) {
                notificacaoService.enviarAlertaSeguranca(event);
            }

            // 3. Atualizar métricas de compliance
            analyticsService.atualizarMetricasCompliance(event);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Erro ao processar evento de auditoria - Key: {}", key, e);
        }
    }

    // ================================
    // MÉTODOS AUXILIARES
    // ================================

    private void verificarAlertas(MovimentoEstoqueEventDTO event) {
        // Verificar se o estoque está baixo após a movimentação
        if (event.getEstoqueAtual() <= 10 && event.getEstoqueAtual() > 0) {
            log.warn("Estoque baixo detectado após movimentação - Produto: {}, Quantidade: {}", 
                event.getProdutoId(), event.getEstoqueAtual());
        } else if (event.getEstoqueAtual() <= 0) {
            log.error("Produto esgotado após movimentação - Produto: {}", event.getProdutoId());
        }
    }

    private void processarProdutoCriado(ProdutoEventDTO event) {
        // Integrar com catálogo de produtos externo
        integracaoExternaService.sincronizarProdutoExterno(event);
        
        // Notificar equipe de compras sobre novo produto
        notificacaoService.notificarNovoProduto(event);
    }

    private void processarProdutoAtualizado(ProdutoEventDTO event) {
        // Sincronizar mudanças com sistemas externos
        integracaoExternaService.atualizarProdutoExterno(event);
        
        // Se houve mudança significativa no preço, notificar
        if (event.getDadosAnteriores() != null && 
            !event.getValorFornecedor().equals(event.getDadosAnteriores().getValorFornecedor())) {
            notificacaoService.notificarMudancaPreco(event);
        }
    }

    private void processarProdutoExcluido(ProdutoEventDTO event) {
        // Remover de sistemas externos
        integracaoExternaService.removerProdutoExterno(event);
        
        // Notificar sobre descontinuação
        notificacaoService.notificarProdutoDescontinuado(event);
    }
} 