package br.com.vortex.application.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuração do Apache Kafka para o sistema NEXDOM.
 * 
 * Esta configuração permite:
 * - Event Sourcing para movimentações de estoque
 * - Streaming de dados em tempo real
 * - Processamento assíncrono de eventos
 * - Integração com sistemas externos
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:vortex-inventory-group}")
    private String groupId;

    // ================================
    // CONFIGURAÇÃO DE TÓPICOS
    // ================================

    @Bean
    public NewTopic movimentoEstoqueTopic() {
        return TopicBuilder.name("vortex.movimento.estoque")
                .partitions(3)
                .replicas(1)
                .config("retention.ms", "604800000") // 7 dias
                .config("cleanup.policy", "delete")
                .build();
    }

    @Bean
    public NewTopic produtoEventsTopic() {
        return TopicBuilder.name("vortex.produto.events")
                .partitions(2)
                .replicas(1)
                .config("retention.ms", "2592000000") // 30 dias
                .build();
    }

    @Bean
    public NewTopic alertasEstoqueTopic() {
        return TopicBuilder.name("vortex.alertas.estoque")
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "259200000") // 3 dias
                .build();
    }

    @Bean
    public NewTopic relatoriosEventsTopic() {
        return TopicBuilder.name("vortex.relatorios.events")
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "86400000") // 1 dia
                .build();
    }

    @Bean
    public NewTopic auditoriaTopic() {
        return TopicBuilder.name("vortex.auditoria")
                .partitions(2)
                .replicas(1)
                .config("retention.ms", "15552000000") // 180 dias
                .config("cleanup.policy", "compact,delete")
                .build();
    }

    // ================================
    // CONFIGURAÇÃO DO PRODUCER
    // ================================

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Configurações para performance e confiabilidade
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ================================
    // CONFIGURAÇÃO DO CONSUMER
    // ================================

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // Configurações para confiabilidade
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        
        // Configuração para deserialização JSON
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "br.com.vortex.application.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "br.com.vortex.application.dto.KafkaEventDTO");
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Configurações do container
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setPollTimeout(3000);
        
        return factory;
    }

    // ================================
    // CONSUMER FACTORY ESPECÍFICO PARA STRINGS
    // ================================

    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-string");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> stringKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stringConsumerFactory());
        return factory;
    }
} 