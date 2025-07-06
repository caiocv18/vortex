package br.com.vortex.application.service;

import br.com.vortex.application.dto.QueueInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço responsável por monitorar filas de mensageria.
 * 
 * Este serviço permite:
 * - Obter informações sobre filas RabbitMQ
 * - Monitorar status do sistema de mensageria
 * - Fornecer estatísticas das filas
 * - Buscar mensagens para diagnóstico
 */
@Slf4j
@Service
public class QueueMonitoringService {

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Value("${message.broker.type:none}")
    private String brokerType;

    @Value("${rabbitmq.enabled:false}")
    private boolean rabbitmqEnabled;

    @Value("${kafka.enabled:false}")
    private boolean kafkaEnabled;

    @Value("${rabbitmq.exchange.name:vortex.exchange}")
    private String exchangeName;

    /**
     * Lista todas as filas configuradas no sistema.
     */
    public List<QueueInfoDTO> listarFilas() {
        List<QueueInfoDTO> filas = new ArrayList<>();
        
        log.info("Listando filas - Broker Type: {}, RabbitMQ: {}, Kafka: {}", 
            brokerType, rabbitmqEnabled, kafkaEnabled);

        if (rabbitmqEnabled && rabbitTemplate != null) {
            filas.addAll(listarFilasRabbitMQ());
        }

        if (kafkaEnabled) {
            filas.addAll(listarFilasKafka());
        }

        if (filas.isEmpty()) {
            // Se não há filas configuradas, retorna filas mockadas para desenvolvimento
            filas.addAll(criarFilasMockadas());
        }

        return filas;
    }

    /**
     * Obtém informações sobre uma fila específica.
     */
    public QueueInfoDTO obterInformacoesFila(String nomeFila) {
        log.info("Obtendo informações da fila: {}", nomeFila);

        if (rabbitmqEnabled && rabbitTemplate != null) {
            return obterInfoFilaRabbitMQ(nomeFila);
        }

        if (kafkaEnabled) {
            return obterInfoFilaKafka(nomeFila);
        }

        // Retorna informação mockada se não há broker configurado
        return criarFilaMockada(nomeFila);
    }

    /**
     * Obtém o status geral do sistema de mensageria.
     */
    public Object obterStatusSistema() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("brokerType", brokerType);
        status.put("rabbitmqEnabled", rabbitmqEnabled);
        status.put("kafkaEnabled", kafkaEnabled);
        status.put("timestamp", LocalDateTime.now());

        if (rabbitmqEnabled && rabbitTemplate != null) {
            try {
                // Tenta fazer um teste de conectividade
                rabbitTemplate.convertAndSend(exchangeName, "test.routing.key", "test-message");
                status.put("rabbitmqStatus", "CONNECTED");
            } catch (Exception e) {
                log.warn("RabbitMQ não está disponível: {}", e.getMessage());
                status.put("rabbitmqStatus", "DISCONNECTED");
                status.put("rabbitmqError", e.getMessage());
            }
        } else {
            status.put("rabbitmqStatus", "DISABLED");
        }

        if (kafkaEnabled) {
            status.put("kafkaStatus", "CONFIGURED");
        } else {
            status.put("kafkaStatus", "DISABLED");
        }

        return status;
    }

    /**
     * Lista filas do RabbitMQ.
     */
    private List<QueueInfoDTO> listarFilasRabbitMQ() {
        List<QueueInfoDTO> filas = new ArrayList<>();
        
        try {
            // Filas configuradas no sistema
            String[] nomesFilas = {
                "vortex.movimento.estoque.queue",
                "vortex.produto.events.queue", 
                "vortex.alertas.estoque.queue",
                "vortex.relatorios.events.queue",
                "vortex.auditoria.queue"
            };

            String[] descricoes = {
                "Fila para eventos de movimentação de estoque",
                "Fila para eventos de produtos",
                "Fila para alertas de estoque",
                "Fila para eventos de relatórios",
                "Fila para eventos de auditoria"
            };

            for (int i = 0; i < nomesFilas.length; i++) {
                QueueInfoDTO fila = new QueueInfoDTO();
                fila.setNome(nomesFilas[i]);
                fila.setDescricao(descricoes[i]);
                fila.setTipo("RabbitMQ");
                fila.setStatus("ACTIVE");
                fila.setMensagens(0L); // Seria obtido via RabbitMQ Management API
                fila.setConsumidores(1);
                fila.setTaxaMensagens(0.0);
                fila.setDuravel(true);
                fila.setExchange(exchangeName);
                fila.setUltimaAtualizacao(LocalDateTime.now());
                
                filas.add(fila);
            }

        } catch (Exception e) {
            log.error("Erro ao listar filas RabbitMQ", e);
        }

        return filas;
    }

    /**
     * Lista filas do Kafka.
     */
    private List<QueueInfoDTO> listarFilasKafka() {
        List<QueueInfoDTO> filas = new ArrayList<>();
        
        // Tópicos Kafka configurados
        String[] topicos = {
            "vortex.movimento.estoque",
            "vortex.produto.events",
            "vortex.alertas.estoque", 
            "vortex.relatorios.events",
            "vortex.auditoria"
        };

        String[] descricoes = {
            "Tópico para eventos de movimentação de estoque",
            "Tópico para eventos de produtos",
            "Tópico para alertas de estoque",
            "Tópico para eventos de relatórios", 
            "Tópico para eventos de auditoria"
        };

        for (int i = 0; i < topicos.length; i++) {
            QueueInfoDTO fila = new QueueInfoDTO();
            fila.setNome(topicos[i]);
            fila.setDescricao(descricoes[i]);
            fila.setTipo("Kafka");
            fila.setStatus("ACTIVE");
            fila.setMensagens(0L);
            fila.setConsumidores(1);
            fila.setTaxaMensagens(0.0);
            fila.setDuravel(true);
            fila.setUltimaAtualizacao(LocalDateTime.now());
            
            filas.add(fila);
        }

        return filas;
    }

    /**
     * Obtém informações de uma fila específica do RabbitMQ.
     */
    private QueueInfoDTO obterInfoFilaRabbitMQ(String nomeFila) {
        QueueInfoDTO fila = new QueueInfoDTO();
        fila.setNome(nomeFila);
        fila.setTipo("RabbitMQ");
        fila.setStatus("ACTIVE");
        fila.setMensagens(0L);
        fila.setConsumidores(1);
        fila.setTaxaMensagens(0.0);
        fila.setDuravel(true);
        fila.setExchange(exchangeName);
        fila.setUltimaAtualizacao(LocalDateTime.now());
        
        return fila;
    }

    /**
     * Obtém informações de um tópico específico do Kafka.
     */
    private QueueInfoDTO obterInfoFilaKafka(String nomeFila) {
        QueueInfoDTO fila = new QueueInfoDTO();
        fila.setNome(nomeFila);
        fila.setTipo("Kafka");
        fila.setStatus("ACTIVE");
        fila.setMensagens(0L);
        fila.setConsumidores(1);
        fila.setTaxaMensagens(0.0);
        fila.setDuravel(true);
        fila.setUltimaAtualizacao(LocalDateTime.now());
        
        return fila;
    }

    /**
     * Cria filas mockadas para desenvolvimento quando nenhum broker está configurado.
     */
    private List<QueueInfoDTO> criarFilasMockadas() {
        List<QueueInfoDTO> filas = new ArrayList<>();
        
        String[] nomes = {
            "sistema.desenvolvimento.queue",
            "logs.aplicacao.queue"
        };

        String[] descricoes = {
            "Fila de desenvolvimento (mockada)",
            "Fila de logs da aplicação (mockada)"
        };

        for (int i = 0; i < nomes.length; i++) {
            QueueInfoDTO fila = new QueueInfoDTO();
            fila.setNome(nomes[i]);
            fila.setDescricao(descricoes[i]);
            fila.setTipo("Mock");
            fila.setStatus("DEVELOPMENT");
            fila.setMensagens(0L);
            fila.setConsumidores(0);
            fila.setTaxaMensagens(0.0);
            fila.setDuravel(false);
            fila.setUltimaAtualizacao(LocalDateTime.now());
            
            filas.add(fila);
        }

        return filas;
    }

    /**
     * Cria uma fila mockada específica.
     */
    private QueueInfoDTO criarFilaMockada(String nomeFila) {
        QueueInfoDTO fila = new QueueInfoDTO();
        fila.setNome(nomeFila);
        fila.setDescricao("Fila de desenvolvimento (mockada)");
        fila.setTipo("Mock");
        fila.setStatus("DEVELOPMENT");
        fila.setMensagens(0L);
        fila.setConsumidores(0);
        fila.setTaxaMensagens(0.0);
        fila.setDuravel(false);
        fila.setUltimaAtualizacao(LocalDateTime.now());
        
        return fila;
    }

    /**
     * Busca mensagens de uma fila específica para diagnóstico.
     * 
     * @param nomeFila Nome da fila
     * @param quantidade Quantidade máxima de mensagens a buscar
     * @return Mapa com informações sobre as mensagens
     */
    public Map<String, Object> buscarMensagens(String nomeFila, int quantidade) {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("nomeFila", nomeFila);
        resultado.put("timestamp", LocalDateTime.now());
        resultado.put("quantidadeSolicitada", quantidade);
        
        if (!rabbitmqEnabled || rabbitTemplate == null) {
            resultado.put("status", "BROKER_DISABLED");
            resultado.put("mensagem", "RabbitMQ não está habilitado ou disponível");
            resultado.put("mensagens", new ArrayList<>());
            return resultado;
        }

        try {
            List<Map<String, Object>> mensagens = new ArrayList<>();
            
            // Tenta buscar mensagens da fila usando receiveAndConvert
            for (int i = 0; i < quantidade; i++) {
                Object mensagem = rabbitTemplate.receiveAndConvert(nomeFila, 1000); // timeout de 1 segundo
                if (mensagem == null) {
                    break; // Não há mais mensagens
                }
                
                Map<String, Object> msgInfo = new HashMap<>();
                msgInfo.put("indice", i + 1);
                msgInfo.put("conteudo", mensagem);
                msgInfo.put("tipo", mensagem.getClass().getSimpleName());
                msgInfo.put("timestampRecebimento", LocalDateTime.now());
                mensagens.add(msgInfo);
            }
            
            if (mensagens.isEmpty()) {
                resultado.put("status", "QUEUE_EMPTY");
                resultado.put("mensagem", "Queue is empty");
            } else {
                resultado.put("status", "SUCCESS");
                resultado.put("mensagem", String.format("Encontradas %d mensagens", mensagens.size()));
            }
            
            resultado.put("mensagens", mensagens);
            resultado.put("totalEncontradas", mensagens.size());
            
        } catch (Exception e) {
            log.error("Erro ao buscar mensagens da fila: {}", nomeFila, e);
            resultado.put("status", "ERROR");
            resultado.put("mensagem", "Erro ao buscar mensagens: " + e.getMessage());
            resultado.put("mensagens", new ArrayList<>());
            resultado.put("totalEncontradas", 0);
        }
        
        return resultado;
    }

    /**
     * Publica uma mensagem de teste em uma fila específica.
     * 
     * @param nomeFila Nome da fila
     * @param mensagem Mensagem a ser publicada
     * @return Resultado da operação
     */
    public Map<String, Object> publicarMensagemTeste(String nomeFila, Map<String, Object> mensagem) {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("nomeFila", nomeFila);
        resultado.put("timestamp", LocalDateTime.now());
        
        if (!rabbitmqEnabled || rabbitTemplate == null) {
            resultado.put("status", "BROKER_DISABLED");
            resultado.put("mensagem", "RabbitMQ não está habilitado ou disponível");
            return resultado;
        }

        try {
            // Adiciona metadados à mensagem
            Map<String, Object> mensagemCompleta = new HashMap<>(mensagem);
            mensagemCompleta.put("_test", true);
            mensagemCompleta.put("_timestamp", LocalDateTime.now());
            mensagemCompleta.put("_source", "QueueController");
            
            // Determina a routing key baseada no nome da fila
            String routingKey = determinarRoutingKey(nomeFila);
            
            // Publica a mensagem
            if (routingKey != null) {
                rabbitTemplate.convertAndSend(exchangeName, routingKey, mensagemCompleta);
                resultado.put("status", "SUCCESS");
                resultado.put("mensagem", "Mensagem de teste publicada com sucesso");
                resultado.put("routingKey", routingKey);
            } else {
                // Publica diretamente na fila se não conseguir determinar routing key
                rabbitTemplate.convertAndSend(nomeFila, mensagemCompleta);
                resultado.put("status", "SUCCESS");
                resultado.put("mensagem", "Mensagem de teste publicada diretamente na fila");
                resultado.put("routingKey", "DIRECT_TO_QUEUE");
            }
            
            resultado.put("conteudo", mensagemCompleta);
            
        } catch (Exception e) {
            log.error("Erro ao publicar mensagem de teste na fila: {}", nomeFila, e);
            resultado.put("status", "ERROR");
            resultado.put("mensagem", "Erro ao publicar mensagem: " + e.getMessage());
        }
        
        return resultado;
    }

    /**
     * Determina a routing key apropriada baseada no nome da fila.
     */
    private String determinarRoutingKey(String nomeFila) {
        switch (nomeFila) {
            case "vortex.movimento.estoque.queue":
                return "vortex.movimento.estoque.test";
            case "vortex.produto.events.queue":
                return "vortex.produto.events.test";
            case "vortex.alertas.estoque.queue":
                return "vortex.alertas.estoque.test";
            case "vortex.relatorios.events.queue":
                return "vortex.relatorios.events.test";
            case "vortex.auditoria.queue":
                return "vortex.auditoria.test";
            default:
                return null;
        }
    }
} 