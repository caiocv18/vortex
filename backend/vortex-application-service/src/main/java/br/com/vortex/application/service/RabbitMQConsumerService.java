package br.com.vortex.application.service;

import br.com.vortex.application.dto.*;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Serviço responsável por consumir eventos do RabbitMQ.
 * 
 * Este serviço processa:
 * - Eventos de movimentação de estoque para analytics
 * - Alertas de estoque para notificações
 * - Eventos de auditoria para compliance
 * - Integração com sistemas externos
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
public class RabbitMQConsumerService {

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private IntegracaoExternaService integracaoExternaService;

    // ================================
    // CONSUMER DE MOVIMENTOS DE ESTOQUE
    // ================================

    @RabbitListener(queues = "vortex.movimento.estoque.queue")
    public void processarMovimentoEstoque(@Payload MovimentoEstoqueEventDTO event,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                        Channel channel) throws IOException {
        try {
            log.info("Processando evento de movimento de estoque via RabbitMQ - Produto: {}, Tipo: {}, Quantidade: {}", 
                event.getProdutoId(), event.getTipoMovimentacao(), event.getQuantidadeMovimentada());

            // 1. Atualizar analytics em tempo real
            analyticsService.processarMovimentoEstoque(event);

            // 2. Verificar se precisa gerar alertas
            verificarAlertas(event);

            // 3. Integrar com sistemas externos (ERP, WMS, etc.)
            integracaoExternaService.sincronizarMovimentoEstoque(event);

            // 4. Atualizar dashboards em tempo real
            notificacaoService.atualizarDashboard(event);

            // Confirmar processamento
            channel.basicAck(deliveryTag, false);
            log.debug("Evento de movimento processado com sucesso via RabbitMQ - RoutingKey: {}", routingKey);

        } catch (Exception e) {
            log.error("Erro ao processar evento de movimento de estoque via RabbitMQ - RoutingKey: {}", routingKey, e);
            // Rejeitar mensagem e reenviar para a fila
            channel.basicNack(deliveryTag, false, true);
        }
    }

    // ================================
    // CONSUMER DE EVENTOS DE PRODUTO
    // ================================

    @RabbitListener(queues = "vortex.produto.events.queue")
    public void processarEventoProduto(@Payload ProdutoEventDTO event,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                     @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                     Channel channel) throws IOException {
        try {
            log.info("Processando evento de produto via RabbitMQ - Ação: {}, Produto: {}", 
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

            // Confirmar processamento
            channel.basicAck(deliveryTag, false);
            log.debug("Evento de produto processado com sucesso via RabbitMQ - RoutingKey: {}", routingKey);

        } catch (Exception e) {
            log.error("Erro ao processar evento de produto via RabbitMQ - RoutingKey: {}", routingKey, e);
            // Rejeitar mensagem e reenviar para a fila
            channel.basicNack(deliveryTag, false, true);
        }
    }

    // ================================
    // CONSUMER DE ALERTAS DE ESTOQUE
    // ================================

    @RabbitListener(queues = "vortex.alertas.estoque.queue")
    public void processarAlertaEstoque(@Payload AlertaEstoqueEventDTO event,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                     @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                     Channel channel) throws IOException {
        try {
            log.warn("Processando alerta de estoque via RabbitMQ - Tipo: {}, Produto: {}, Quantidade: {}", 
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

            // Confirmar processamento
            channel.basicAck(deliveryTag, false);
            log.debug("Alerta de estoque processado com sucesso via RabbitMQ - RoutingKey: {}", routingKey);

        } catch (Exception e) {
            log.error("Erro ao processar alerta de estoque via RabbitMQ - RoutingKey: {}", routingKey, e);
            // Rejeitar mensagem e reenviar para a fila
            channel.basicNack(deliveryTag, false, true);
        }
    }

    // ================================
    // CONSUMER DE AUDITORIA
    // ================================

    @RabbitListener(queues = "vortex.auditoria.queue")
    public void processarAuditoria(@Payload AuditoriaEventDTO event,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                 @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                                 Channel channel) throws IOException {
        try {
            log.debug("Processando evento de auditoria via RabbitMQ - Ação: {}, Entidade: {}", 
                event.getAcao(), event.getEntidade());

            // 1. Armazenar em sistema de auditoria
            analyticsService.registrarAuditoria(event);

            // 2. Verificar se é uma operação suspeita
            if (analyticsService.isOperacaoSuspeita(event)) {
                notificacaoService.enviarAlertaSeguranca(event);
            }

            // 3. Atualizar métricas de compliance
            analyticsService.atualizarMetricasCompliance(event);

            // Confirmar processamento
            channel.basicAck(deliveryTag, false);
            log.debug("Evento de auditoria processado com sucesso via RabbitMQ - RoutingKey: {}", routingKey);

        } catch (Exception e) {
            log.error("Erro ao processar evento de auditoria via RabbitMQ - RoutingKey: {}", routingKey, e);
            // Rejeitar mensagem e reenviar para a fila
            channel.basicNack(deliveryTag, false, true);
        }
    }

    // ================================
    // MÉTODOS AUXILIARES
    // ================================

    private void verificarAlertas(MovimentoEstoqueEventDTO event) {
        // Verificar se o estoque está baixo após a movimentação
        if (event.getEstoqueAtual() <= 10 && event.getEstoqueAtual() > 0) {
            log.warn("Estoque baixo detectado após movimentação via RabbitMQ - Produto: {}, Quantidade: {}", 
                event.getProdutoId(), event.getEstoqueAtual());
        } else if (event.getEstoqueAtual() <= 0) {
            log.error("Produto esgotado após movimentação via RabbitMQ - Produto: {}", event.getProdutoId());
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