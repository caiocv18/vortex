package br.com.vortex.desafio.backend.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * DTO para eventos de produto no Kafka.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProdutoEventDTO extends KafkaEventDTO {
    
    /**
     * Ação realizada (CREATED, UPDATED, DELETED)
     */
    private String action;
    
    /**
     * ID do produto
     */
    private Long produtoId;
    
    /**
     * Descrição do produto
     */
    private String descricao;
    
    /**
     * Valor do fornecedor
     */
    private BigDecimal valorFornecedor;
    
    /**
     * Quantidade em estoque
     */
    private Integer quantidadeEmEstoque;
    
    /**
     * ID do tipo de produto
     */
    private Long tipoProdutoId;
    
    /**
     * Nome do tipo de produto
     */
    private String tipoProdutoNome;
    
    /**
     * Dados anteriores do produto (para updates)
     */
    private ProdutoDTO dadosAnteriores;

    public ProdutoEventDTO() {
        super("PRODUTO_EVENT");
    }
} 