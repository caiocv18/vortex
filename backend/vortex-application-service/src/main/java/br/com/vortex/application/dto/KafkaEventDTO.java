package br.com.vortex.application.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO base para eventos Kafka no sistema NEXDOM.
 * 
 * Utiliza polimorfismo JSON para suportar diferentes tipos de eventos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MovimentoEstoqueEventDTO.class, name = "MOVIMENTO_ESTOQUE"),
    @JsonSubTypes.Type(value = ProdutoEventDTO.class, name = "PRODUTO_EVENT"),
    @JsonSubTypes.Type(value = AlertaEstoqueEventDTO.class, name = "ALERTA_ESTOQUE"),
    @JsonSubTypes.Type(value = AuditoriaEventDTO.class, name = "AUDITORIA")
})
public abstract class KafkaEventDTO {
    
    /**
     * ID único do evento
     */
    private String eventId = UUID.randomUUID().toString();
    
    /**
     * Timestamp do evento
     */
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Tipo do evento
     */
    private String eventType;
    
    /**
     * Versão do schema do evento
     */
    private String version = "1.0";
    
    /**
     * ID da sessão/transação que originou o evento
     */
    private String sessionId;
    
    /**
     * ID do usuário que originou o evento
     */
    private String userId;
    
    /**
     * Informações adicionais do evento
     */
    private String metadata;

    protected KafkaEventDTO(String eventType) {
        this.eventType = eventType;
    }
} 