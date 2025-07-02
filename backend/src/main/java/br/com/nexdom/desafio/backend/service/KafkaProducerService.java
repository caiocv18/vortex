package br.com.nexdom.desafio.backend.service;

import br.com.nexdom.desafio.backend.dto.*;
import br.com.nexdom.desafio.backend.model.MovimentoEstoque;
import br.com.nexdom.desafio.backend.model.Produto;
import br.com.nexdom.desafio.backend.model.enums.TipoMovimentacao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço responsável por publicar eventos no Apache Kafka.
 * 
 * Este serviço permite:
 * - Event Sourcing para todas as operações do sistema
 * - Integração assíncrona com sistemas externos
 * - Auditoria completa de operações
 * - Alertas em tempo real
 * - Fallback quando Kafka não está disponível
 */
@Slf4j
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.movimento-estoque:nexdom.movimento.estoque}")
    private String movimentoEstoqueTopic;

    @Value("${kafka.topics.produto-events:nexdom.produto.events}")
    private String produtoEventsTopic;

    @Value("${kafka.topics.alertas-estoque:nexdom.alertas.estoque}")
    private String alertasEstoqueTopic;

    @Value("${kafka.topics.relatorios-events:nexdom.relatorios.events}")
    private String relatoriosEventsTopic;

    @Value("${kafka.topics.auditoria:nexdom.auditoria}")
    private String auditoriaTopic;

    @Value("${kafka.enabled:true}")
    private boolean kafkaEnabled;

    @Value("${kafka.fallback.enabled:true}")
    private boolean fallbackEnabled;

    @Value("${kafka.connection.timeout:5000}")
    private int connectionTimeout;

    @Autowired
    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publica evento de movimentação de estoque.
     */
    public void publicarMovimentoEstoque(MovimentoEstoque movimento, Produto produto, 
                                       Integer estoqueAnterior, String userId) {
        if (!kafkaEnabled) {
            log.debug("Kafka desabilitado - evento de movimento não será publicado");
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

            String key = "produto-" + produto.getId();
            publishEventWithFallback(movimentoEstoqueTopic, key, event, "movimento de estoque");

        } catch (Exception e) {
            log.error("Erro ao publicar evento de movimento de estoque", e);
            if (fallbackEnabled) {
                log.info("Fallback ativado - operação continuará sem Kafka");
            }
        }
    }

    /**
     * Publica evento de criação de produto.
     */
    public void publicarProdutoCriado(Produto produto, String userId) {
        publicarEventoProduto("CREATED", produto, null, userId);
    }

    /**
     * Publica evento de atualização de produto.
     */
    public void publicarProdutoAtualizado(Produto produto, ProdutoDTO dadosAnteriores, String userId) {
        publicarEventoProduto("UPDATED", produto, dadosAnteriores, userId);
    }

    /**
     * Publica evento de exclusão de produto.
     */
    public void publicarProdutoExcluido(Produto produto, String userId) {
        publicarEventoProduto("DELETED", produto, null, userId);
    }

    private void publicarEventoProduto(String action, Produto produto, ProdutoDTO dadosAnteriores, String userId) {
        if (!kafkaEnabled) return;

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

            String key = "produto-" + produto.getId();
            publishEventWithFallback(produtoEventsTopic, key, event, "produto " + action);

        } catch (Exception e) {
            log.error("Erro ao publicar evento de produto", e);
            if (fallbackEnabled) {
                log.info("Fallback ativado - operação continuará sem Kafka");
            }
        }
    }

    /**
     * Publica alerta de estoque baixo.
     */
    public void publicarAlertaEstoqueBaixo(Produto produto, Integer quantidadeMinima, String userId) {
        publicarAlertaEstoque("ESTOQUE_BAIXO", produto, quantidadeMinima, "MEDIUM", 
            "Estoque baixo detectado para o produto: " + produto.getDescricao(), false, userId);
    }

    /**
     * Publica alerta de estoque esgotado.
     */
    public void publicarAlertaEstoqueEsgotado(Produto produto, String userId) {
        publicarAlertaEstoque("ESTOQUE_ESGOTADO", produto, 0, "HIGH", 
            "Produto esgotado: " + produto.getDescricao(), true, userId);
    }

    /**
     * Publica alerta de estoque crítico.
     */
    public void publicarAlertaEstoqueCritico(Produto produto, Integer quantidadeMinima, String userId) {
        publicarAlertaEstoque("ESTOQUE_CRITICO", produto, quantidadeMinima, "CRITICAL", 
            "Estoque crítico para o produto: " + produto.getDescricao(), true, userId);
    }

    private void publicarAlertaEstoque(String tipoAlerta, Produto produto, Integer quantidadeMinima, 
                                     String prioridade, String mensagem, Boolean acaoImediata, String userId) {
        if (!kafkaEnabled) return;

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

            String key = "alerta-produto-" + produto.getId();
            publishEventWithFallback(alertasEstoqueTopic, key, event, "alerta de estoque " + tipoAlerta);

        } catch (Exception e) {
            log.error("Erro ao publicar alerta de estoque", e);
            if (fallbackEnabled) {
                log.info("Fallback ativado - operação continuará sem Kafka");
            }
        }
    }

    /**
     * Publica evento de auditoria.
     */
    public void publicarAuditoria(String acao, String entidade, Long entidadeId, 
                                String detalhes, String userId, String status, String erro) {
        if (!kafkaEnabled) return;

        try {
            AuditoriaEventDTO event = new AuditoriaEventDTO();
            event.setAcao(acao);
            event.setEntidade(entidade);
            event.setEntidadeId(entidadeId);
            event.setDetalhes(detalhes);
            event.setResultado(status);
            event.setMensagemErro(erro);
            event.setUserId(userId);

            String key = "auditoria-" + entidade + "-" + entidadeId;
            publishEventWithFallback(auditoriaTopic, key, event, "auditoria " + acao);

        } catch (Exception e) {
            log.error("Erro ao publicar evento de auditoria", e);
            if (fallbackEnabled) {
                log.info("Fallback ativado - operação continuará sem Kafka");
            }
        }
    }

    /**
     * Método auxiliar para publicar eventos com fallback.
     */
    private void publishEventWithFallback(String topic, String key, Object event, String eventType) {
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Evento {} publicado com sucesso - Topic: {}, Key: {}", eventType, topic, key);
                } else {
                    log.error("Erro ao publicar evento {} - Topic: {}, Key: {}", eventType, topic, key, ex);
                    if (fallbackEnabled) {
                        log.info("Fallback ativado para evento {} - operação continuará", eventType);
                    }
                }
            });

        } catch (Exception e) {
            log.error("Erro crítico ao tentar publicar evento {} - Topic: {}", eventType, topic, e);
            if (fallbackEnabled) {
                log.info("Fallback ativado - sistema continuará funcionando sem Kafka");
            } else {
                throw new RuntimeException("Falha crítica no Kafka e fallback desabilitado", e);
            }
        }
    }

    /**
     * Verifica se o Kafka está disponível.
     */
    public boolean isKafkaAvailable() {
        if (!kafkaEnabled) {
            return false;
        }
        
        try {
            // Tenta enviar um evento de teste
            kafkaTemplate.send("test-topic", "test-key", "test-message");
            return true;
        } catch (Exception e) {
            log.warn("Kafka não está disponível: {}", e.getMessage());
            return false;
        }
    }
} 