package br.com.nexdom.desafio.backend.dto;

import br.com.nexdom.desafio.backend.model.enums.TipoMovimentacao;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO para eventos de movimentação de estoque no Kafka.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MovimentoEstoqueEventDTO extends KafkaEventDTO {
    
    /**
     * ID do movimento de estoque
     */
    private Long movimentoId;
    
    /**
     * ID do produto
     */
    private Long produtoId;
    
    /**
     * Descrição do produto
     */
    private String produtoDescricao;
    
    /**
     * Tipo de movimentação (ENTRADA/SAIDA)
     */
    private TipoMovimentacao tipoMovimentacao;
    
    /**
     * Quantidade movimentada
     */
    private Integer quantidadeMovimentada;
    
    /**
     * Valor da venda (para saídas)
     */
    private BigDecimal valorVenda;
    
    /**
     * Valor do fornecedor
     */
    private BigDecimal valorFornecedor;
    
    /**
     * Quantidade em estoque antes da movimentação
     */
    private Integer estoqueAnterior;
    
    /**
     * Quantidade em estoque após a movimentação
     */
    private Integer estoqueAtual;
    
    /**
     * Data da movimentação
     */
    private LocalDateTime dataMovimento;
    
    /**
     * Lucro da operação (para saídas)
     */
    private BigDecimal lucro;
    
    /**
     * Tipo de produto
     */
    private String tipoProduto;

    public MovimentoEstoqueEventDTO() {
        super("MOVIMENTO_ESTOQUE");
    }
} 