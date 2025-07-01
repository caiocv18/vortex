package br.com.nexdom.desafio.backend.dto;

import br.com.nexdom.desafio.backend.model.enums.TipoMovimentacao;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO para mensagens SQS relacionadas a movimentos de estoque.
 * Usado para comunicação assíncrona entre serviços via Amazon SQS.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovimentoEstoqueMessageDTO {
    
    /**
     * Identificador único da operação para rastreamento.
     */
    private String operationId;
    
    /**
     * Timestamp da criação da mensagem.
     */
    private LocalDateTime timestamp;
    
    /**
     * Tipo de movimentação (ENTRADA ou SAIDA).
     */
    private TipoMovimentacao tipoMovimentacao;
    
    /**
     * Quantidade a ser movimentada.
     */
    private Integer quantidadeMovimentada;
    
    /**
     * ID do produto relacionado.
     */
    private Long produtoId;
    
    /**
     * Valor do fornecedor (para cálculos).
     */
    private BigDecimal valorFornecedor;
    
    /**
     * Identificador do usuário que iniciou a operação.
     */
    private String usuarioId;
    
    /**
     * Prioridade da mensagem (HIGH, NORMAL, LOW).
     */
    private String prioridade = "NORMAL";
    
    /**
     * Número de tentativas de processamento.
     */
    private Integer tentativas = 0;
    
    /**
     * Motivo em caso de erro (para Dead Letter Queue).
     */
    private String motivoErro;
} 