package br.com.vortex.application.service;

import br.com.vortex.application.dto.*;
import br.com.vortex.application.model.MovimentoEstoque;
import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.enums.TipoMovimentacao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Serviço responsável por publicar eventos no RabbitMQ.
 * 
 * Este serviço permite:
 * - Event Sourcing para todas as operações do sistema
 * - Integração assíncrona com sistemas externos
 * - Auditoria completa de operações
 * - Alertas em tempo real
 * - Fallback quando RabbitMQ não está disponível
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
public class RabbitMQProducerService implements MessageBrokerService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.name:vortex.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.enabled:true}")
    private boolean rabbitMQEnabled;

    @Value("${rabbitmq.fallback.enabled:true}")
    private boolean fallbackEnabled;

    @Autowired
    public RabbitMQProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publicarMovimentoEstoque(MovimentoEstoque movimento, Produto produto, 
                                       Integer estoqueAnterior, String userId) {
        if (!rabbitMQEnabled) {
            log.debug("RabbitMQ desabilitado - evento de movimento não será publicado");
            return;
        }

        try {
            MovimentoEstoqueEventDTO event = new MovimentoEstoqueEventDTO();
            event.setMovimentoId(movimento.getId());
            event.setProdutoId(produto.getId());
            event.setProdutoDescricao(produto.getDescricao());
            event.setTipoMovimentacao(movimento.getTipoMovimentacao());
            event.setQuantidadeMovimentada(movimento.getQuantidadeMovimentada());
            event.setValorVenda(movimento.getValorVenda());
            event.setValorFornecedor(produto.getValorFornecedor());
            event.setEstoqueAnterior(estoqueAnterior);
            event.setEstoqueAtual(produto.getQuantidadeEmEstoque());
            event.setDataMovimento(movimento.getDataMovimento());
            event.setTipoProduto(produto.getTipoProduto().getNome());
            event.setUserId(userId);

            // Calcular lucro para saídas
            if (movimento.getTipoMovimentacao() == TipoMovimentacao.SAIDA && movimento.getValorVenda() != null) {
                BigDecimal custoTotal = produto.getValorFornecedor()
                    .multiply(BigDecimal.valueOf(movimento.getQuantidadeMovimentada()));
                event.setLucro(movimento.getValorVenda().subtract(custoTotal));
            }

            String routingKey = "vortex.movimento.estoque.created";
            publishEventWithFallback(routingKey, event, "movimento de estoque");

        } catch (Exception e) {
            log.error("Erro ao publicar evento de movimento de estoque via RabbitMQ", e);
            if (fallbackEnabled) {
                log.info("Fallback ativado - operação continuará sem RabbitMQ");
            }
        }
    }

    @Override
    public void publicarProdutoCriado(Produto produto, String userId) {
        publicarEventoProduto("CREATED", produto, null, userId);
    }

    @Override
    public void publicarProdutoAtualizado(Produto produto, ProdutoDTO dadosAnteriores, String userId) {
        publicarEventoProduto("UPDATED", produto, dadosAnteriores, userId);
    }

    @Override
    public void publicarProdutoExcluido(Produto produto, String userId) {
        publicarEventoProduto("DELETED", produto, null, userId);
    }

    private void publicarEventoProduto(String action, Produto produto, ProdutoDTO dadosAnteriores, String userId) {
        if (!rabbitMQEnabled) return;

        try {
            ProdutoEventDTO event = new ProdutoEventDTO();
            event.setAction(action);
            event.setProdutoId(produto.getId());
            event.setDescricao(produto.getDescricao());
            event.setValorFornecedor(produto.getValorFornecedor());
            event.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque());
            event.setTipoProdutoId(produto.getTipoProduto().getId());
            event.setTipoProdutoNome(produto.getTipoProduto().getNome());
            event.setDadosAnteriores(dadosAnteriores);
            event.setUserId(userId);

            String routingKey = "vortex.produto.events." + action.toLowerCase();
            publishEventWithFallback(routingKey, event, "produto " + action);

        } catch (Exception e) {
            log.error("Erro ao publicar evento de produto via RabbitMQ", e);
            if (fallbackEnabled) {
                log.info("Fallback ativado - operação continuará sem RabbitMQ");
            }
        }
    }

    @Override
    public void publicarAlertaEstoqueBaixo(Produto produto, Integer quantidadeMinima, String userId) {
        publicarAlertaEstoque("ESTOQUE_BAIXO", produto, quantidadeMinima, "MEDIUM", 
            "Estoque baixo detectado para o produto: " + produto.getDescricao(), false, userId);
    }

    @Override
    public void publicarAlertaEstoqueEsgotado(Produto produto, String userId) {
        publicarAlertaEstoque("ESTOQUE_ESGOTADO", produto, 0, "HIGH", 
            "Produto esgotado: " + produto.getDescricao(), true, userId);
    }

    @Override
    public void publicarAlertaEstoqueCritico(Produto produto, Integer quantidadeMinima, String userId) {
        publicarAlertaEstoque("ESTOQUE_CRITICO", produto, quantidadeMinima, "CRITICAL", 
            "Estoque crítico para o produto: " + produto.getDescricao(), true, userId);
    }

    private void publicarAlertaEstoque(String tipoAlerta, Produto produto, Integer quantidadeMinima, 
                                     String prioridade, String mensagem, Boolean acaoImediata, String userId) {
        if (!rabbitMQEnabled) return;

        try {
            AlertaEstoqueEventDTO event = new AlertaEstoqueEventDTO();
            event.setTipoAlerta(tipoAlerta);
            event.setProdutoId(produto.getId());
            event.setProdutoDescricao(produto.getDescricao());
            event.setQuantidadeAtual(produto.getQuantidadeEmEstoque());
            event.setQuantidadeMinima(quantidadeMinima);
            event.setTipoProduto(produto.getTipoProduto().getNome());
            event.setPrioridade(prioridade);
            event.setMensagem(mensagem);
            event.setAcaoImediata(acaoImediata);
            event.setUserId(userId);

            String routingKey = "vortex.alertas.estoque." + tipoAlerta.toLowerCase();
            publishEventWithFallback(routingKey, event, "alerta de estoque " + tipoAlerta);

        } catch (Exception e) {
            log.error("Erro ao publicar alerta de estoque via RabbitMQ", e);
            if (fallbackEnabled) {
                log.info("Fallback ativado - operação continuará sem RabbitMQ");
            }
        }
    }

    @Override
    public void publicarAuditoria(String acao, String entidade, Long entidadeId, 
                                String detalhes, String userId, String status, String erro) {
        if (!rabbitMQEnabled) return;

        try {
            AuditoriaEventDTO event = new AuditoriaEventDTO();
            event.setAcao(acao);
            event.setEntidade(entidade);
            event.setEntidadeId(entidadeId);
            event.setDetalhes(detalhes);
            event.setResultado(status);
            event.setMensagemErro(erro);
            event.setUserId(userId);

            String routingKey = "vortex.auditoria." + acao.toLowerCase();
            publishEventWithFallback(routingKey, event, "auditoria " + acao);

        } catch (Exception e) {
            log.error("Erro ao publicar evento de auditoria via RabbitMQ", e);
            if (fallbackEnabled) {
                log.info("Fallback ativado - operação continuará sem RabbitMQ");
            }
        }
    }

    @Override
    public boolean isAvailable() {
        if (!rabbitMQEnabled) {
            return false;
        }
        
        try {
            // Tenta enviar uma mensagem de teste
            rabbitTemplate.convertAndSend(exchangeName, "test.routing.key", "test-message");
            return true;
        } catch (Exception e) {
            log.warn("RabbitMQ não está disponível: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getType() {
        return "RabbitMQ";
    }

    /**
     * Método auxiliar para publicar eventos com fallback.
     */
    private void publishEventWithFallback(String routingKey, Object event, String eventType) {
        try {
            rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
            log.debug("Evento {} publicado com sucesso via RabbitMQ - RoutingKey: {}", eventType, routingKey);
        } catch (Exception e) {
            log.error("Erro crítico ao tentar publicar evento {} via RabbitMQ - RoutingKey: {}", eventType, routingKey, e);
            if (fallbackEnabled) {
                log.info("Fallback ativado - sistema continuará funcionando sem RabbitMQ");
            } else {
                throw new RuntimeException("Falha crítica no RabbitMQ e fallback desabilitado", e);
            }
        }
    }
} 