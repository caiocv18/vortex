package br.com.vortex.application.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do RabbitMQ para o sistema VORTEX.
 * 
 * Esta configuração permite:
 * - Event Sourcing para movimentações de estoque
 * - Processamento assíncrono de eventos
 * - Integração com sistemas externos
 * - Padrão pub/sub para notificações
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange.name:vortex.exchange}")
    private String exchangeName;

    // ================================
    // EXCHANGE CONFIGURATION
    // ================================

    @Bean
    public TopicExchange vortexExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    // ================================
    // QUEUE CONFIGURATION
    // ================================

    @Bean
    public Queue movimentoEstoqueQueue() {
        return QueueBuilder.durable("vortex.movimento.estoque.queue")
                .withArgument("x-message-ttl", 604800000) // 7 dias
                .build();
    }

    @Bean
    public Queue produtoEventsQueue() {
        return QueueBuilder.durable("vortex.produto.events.queue")
                .withArgument("x-message-ttl", 2592000000L) // 30 dias
                .build();
    }

    @Bean
    public Queue alertasEstoqueQueue() {
        return QueueBuilder.durable("vortex.alertas.estoque.queue")
                .withArgument("x-message-ttl", 259200000) // 3 dias
                .build();
    }

    @Bean
    public Queue relatoriosEventsQueue() {
        return QueueBuilder.durable("vortex.relatorios.events.queue")
                .withArgument("x-message-ttl", 86400000) // 1 dia
                .build();
    }

    @Bean
    public Queue auditoriaQueue() {
        return QueueBuilder.durable("vortex.auditoria.queue")
                .withArgument("x-message-ttl", 15552000000L) // 180 dias
                .build();
    }

    // ================================
    // BINDINGS CONFIGURATION
    // ================================

    @Bean
    public Binding movimentoEstoqueBinding() {
        return BindingBuilder.bind(movimentoEstoqueQueue())
                .to(vortexExchange())
                .with("vortex.movimento.estoque.*");
    }

    @Bean
    public Binding produtoEventsBinding() {
        return BindingBuilder.bind(produtoEventsQueue())
                .to(vortexExchange())
                .with("vortex.produto.events.*");
    }

    @Bean
    public Binding alertasEstoqueBinding() {
        return BindingBuilder.bind(alertasEstoqueQueue())
                .to(vortexExchange())
                .with("vortex.alertas.estoque.*");
    }

    @Bean
    public Binding relatoriosEventsBinding() {
        return BindingBuilder.bind(relatoriosEventsQueue())
                .to(vortexExchange())
                .with("vortex.relatorios.events.*");
    }

    @Bean
    public Binding auditoriaBinding() {
        return BindingBuilder.bind(auditoriaQueue())
                .to(vortexExchange())
                .with("vortex.auditoria.*");
    }

    // ================================
    // RABBIT TEMPLATE CONFIGURATION
    // ================================

    @Bean
    public MessageConverter jsonMessageConverter() {
        // Spring AMQP 4: conversor JSON do Jackson 3 (substitui o Jackson2JsonMessageConverter).
        // O construtor padrão passou a confiar só em java.util/java.lang no cabeçalho __TypeId__
        // (o antigo confiava em tudo); os eventos da aplicação vêm do pacote dto.
        return new JacksonJsonMessageConverter("br.com.vortex.application.dto");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        
        // Configurações para confiabilidade
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                System.err.println("Falha ao enviar mensagem: " + cause);
            }
        });
        
        template.setReturnsCallback(returned -> {
            System.err.println("Mensagem retornada: " + returned.getMessage());
        });
        
        return template;
    }
} 