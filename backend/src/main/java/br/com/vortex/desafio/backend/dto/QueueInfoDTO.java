package br.com.vortex.desafio.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para informações sobre filas de mensageria.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueueInfoDTO {
    
    /**
     * Nome da fila
     */
    private String nome;
    
    /**
     * Número de mensagens na fila
     */
    private Long mensagens;
    
    /**
     * Número de consumidores ativos
     */
    private Integer consumidores;
    
    /**
     * Taxa de mensagens por segundo
     */
    private Double taxaMensagens;
    
    /**
     * Status da fila (ativa, inativa, com erro)
     */
    private String status;
    
    /**
     * Tipo de fila (RabbitMQ, Kafka, SQS)
     */
    private String tipo;
    
    /**
     * Descrição da fila
     */
    private String descricao;
    
    /**
     * Data da última atualização
     */
    private LocalDateTime ultimaAtualizacao;
    
    /**
     * Indica se a fila está durável
     */
    private Boolean duravel;
    
    /**
     * TTL das mensagens (em milissegundos)
     */
    private Long ttl;
    
    /**
     * Exchange associado (para RabbitMQ)
     */
    private String exchange;
    
    /**
     * Routing key (para RabbitMQ)
     */
    private String routingKey;
} 