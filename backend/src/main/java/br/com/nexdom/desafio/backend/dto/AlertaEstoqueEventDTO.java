package br.com.nexdom.desafio.backend.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DTO para eventos de alerta de estoque no Kafka.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AlertaEstoqueEventDTO extends KafkaEventDTO {
    
    /**
     * Tipo de alerta (ESTOQUE_BAIXO, ESTOQUE_ESGOTADO, ESTOQUE_CRITICO)
     */
    private String tipoAlerta;
    
    /**
     * ID do produto
     */
    private Long produtoId;
    
    /**
     * Descrição do produto
     */
    private String produtoDescricao;
    
    /**
     * Quantidade atual em estoque
     */
    private Integer quantidadeAtual;
    
    /**
     * Quantidade mínima configurada
     */
    private Integer quantidadeMinima;
    
    /**
     * Tipo de produto
     */
    private String tipoProduto;
    
    /**
     * Prioridade do alerta (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String prioridade;
    
    /**
     * Mensagem do alerta
     */
    private String mensagem;
    
    /**
     * Indica se é necessária ação imediata
     */
    private Boolean acaoImediata;

    public AlertaEstoqueEventDTO() {
        super("ALERTA_ESTOQUE");
    }
} 