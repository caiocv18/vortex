package br.com.vortex.application.service;

import br.com.vortex.application.dto.*;
import br.com.vortex.application.model.MovimentoEstoque;
import br.com.vortex.application.model.Produto;

/**
 * Interface para serviços de message broker.
 * Implementa o padrão Strategy para permitir diferentes implementações (Kafka, RabbitMQ).
 */
public interface MessageBrokerService {

    /**
     * Publica evento de movimentação de estoque.
     */
    void publicarMovimentoEstoque(MovimentoEstoque movimento, Produto produto, 
                                 Integer estoqueAnterior, String userId);

    /**
     * Publica evento de criação de produto.
     */
    void publicarProdutoCriado(Produto produto, String userId);

    /**
     * Publica evento de atualização de produto.
     */
    void publicarProdutoAtualizado(Produto produto, ProdutoDTO dadosAnteriores, String userId);

    /**
     * Publica evento de exclusão de produto.
     */
    void publicarProdutoExcluido(Produto produto, String userId);

    /**
     * Publica alerta de estoque baixo.
     */
    void publicarAlertaEstoqueBaixo(Produto produto, Integer quantidadeMinima, String userId);

    /**
     * Publica alerta de estoque esgotado.
     */
    void publicarAlertaEstoqueEsgotado(Produto produto, String userId);

    /**
     * Publica alerta de estoque crítico.
     */
    void publicarAlertaEstoqueCritico(Produto produto, Integer quantidadeMinima, String userId);

    /**
     * Publica evento de auditoria.
     */
    void publicarAuditoria(String acao, String entidade, Long entidadeId, 
                          String detalhes, String userId, String status, String erro);

    /**
     * Verifica se o message broker está disponível.
     */
    boolean isAvailable();

    /**
     * Retorna o tipo do message broker.
     */
    String getType();
} 