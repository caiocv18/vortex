package br.com.vortex.desafio.backend.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DTO para eventos de auditoria no Kafka.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditoriaEventDTO extends KafkaEventDTO {
    
    /**
     * Ação realizada
     */
    private String acao;
    
    /**
     * Entidade afetada
     */
    private String entidade;
    
    /**
     * ID da entidade
     */
    private Long entidadeId;
    
    /**
     * Detalhes da operação
     */
    private String detalhes;
    
    /**
     * IP do usuário
     */
    private String ipUsuario;
    
    /**
     * User Agent
     */
    private String userAgent;
    
    /**
     * Resultado da operação (SUCCESS, FAILURE, ERROR)
     */
    private String resultado;
    
    /**
     * Código de erro (se aplicável)
     */
    private String codigoErro;
    
    /**
     * Mensagem de erro (se aplicável)
     */
    private String mensagemErro;
    
    /**
     * Duração da operação em milissegundos
     */
    private Long duracao;

    public AuditoriaEventDTO() {
        super("AUDITORIA");
    }
} 