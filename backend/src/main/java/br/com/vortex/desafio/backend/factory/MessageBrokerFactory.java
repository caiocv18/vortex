package br.com.vortex.desafio.backend.factory;

import br.com.vortex.desafio.backend.service.KafkaProducerService;
import br.com.vortex.desafio.backend.service.MessageBrokerService;
import br.com.vortex.desafio.backend.service.RabbitMQProducerService;
import br.com.vortex.desafio.backend.model.MovimentoEstoque;
import br.com.vortex.desafio.backend.model.Produto;
import br.com.vortex.desafio.backend.dto.ProdutoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Factory responsável por escolher o serviço de message broker.
 * 
 * Implementa o padrão Factory para decidir entre:
 * - Kafka: Event sourcing e streaming em tempo real
 * - RabbitMQ: Mensageria tradicional com filas
 * - Nenhum: Processamento síncrono apenas
 */
@Slf4j
@Configuration
public class MessageBrokerFactory {

    @Value("${message.broker.type:kafka}")
    private String brokerType;

    @Value("${kafka.enabled:false}")
    private boolean kafkaEnabled;

    @Value("${rabbitmq.enabled:false}")
    private boolean rabbitmqEnabled;

    @Autowired(required = false)
    private KafkaProducerService kafkaProducerService;

    @Autowired(required = false)
    private RabbitMQProducerService rabbitMQProducerService;

    /**
     * Cria o bean principal do MessageBrokerService baseado na configuração.
     */
    @Bean
    @Primary
    public MessageBrokerService messageBrokerService() {
        log.info("Configurando Message Broker - Tipo: {}, Kafka: {}, RabbitMQ: {}", 
            brokerType, kafkaEnabled, rabbitmqEnabled);

        switch (brokerType.toLowerCase()) {
            case "kafka":
                return createKafkaService();
            case "rabbitmq":
                return createRabbitMQService();
            case "none":
                return createNoOpService();
            default:
                log.warn("Tipo de message broker desconhecido: {}. Usando Kafka como padrão.", brokerType);
                return createKafkaService();
        }
    }

    /**
     * Cria serviço Kafka se disponível.
     */
    private MessageBrokerService createKafkaService() {
        if (kafkaEnabled && kafkaProducerService != null) {
            log.info("Usando Apache Kafka como message broker");
            return kafkaProducerService;
        } else {
            log.warn("Kafka não está disponível. Usando serviço NoOp.");
            return createNoOpService();
        }
    }

    /**
     * Cria serviço RabbitMQ se disponível.
     */
    private MessageBrokerService createRabbitMQService() {
        if (rabbitmqEnabled && rabbitMQProducerService != null) {
            log.info("Usando RabbitMQ como message broker");
            return rabbitMQProducerService;
        } else {
            log.warn("RabbitMQ não está disponível. Usando serviço NoOp.");
            return createNoOpService();
        }
    }



    /**
     * Cria serviço NoOp para quando nenhum message broker está disponível.
     */
    private MessageBrokerService createNoOpService() {
        log.info("Nenhum message broker configurado. Usando serviço NoOp.");
        return new NoOpMessageBrokerService();
    }

    /**
     * Serviço que não faz nada - para quando message broker está desabilitado.
     */
    private static class NoOpMessageBrokerService implements MessageBrokerService {
        @Override
        public void publicarMovimentoEstoque(MovimentoEstoque movimento, Produto produto, 
                                           Integer estoqueAnterior, String userId) {
            // No-op
        }

        @Override
        public void publicarProdutoCriado(Produto produto, String userId) {
            // No-op
        }

        @Override
        public void publicarProdutoAtualizado(Produto produto, ProdutoDTO dadosAnteriores, String userId) {
            // No-op
        }

        @Override
        public void publicarProdutoExcluido(Produto produto, String userId) {
            // No-op
        }

        @Override
        public void publicarAlertaEstoqueBaixo(Produto produto, Integer quantidadeMinima, String userId) {
            // No-op
        }

        @Override
        public void publicarAlertaEstoqueEsgotado(Produto produto, String userId) {
            // No-op
        }

        @Override
        public void publicarAlertaEstoqueCritico(Produto produto, Integer quantidadeMinima, String userId) {
            // No-op
        }

        @Override
        public void publicarAuditoria(String acao, String entidade, Long entidadeId, 
                                    String detalhes, String userId, String status, String erro) {
            // No-op
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String getType() {
            return "NoOp";
        }
    }


} 