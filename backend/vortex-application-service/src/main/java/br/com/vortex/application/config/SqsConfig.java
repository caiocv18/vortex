package br.com.vortex.application.config;

import br.com.vortex.application.repository.MovimentoEstoqueRepository;
import br.com.vortex.application.repository.ProdutoRepository;
import br.com.vortex.application.service.SqsConsumerService;
import br.com.vortex.application.service.SqsProducerService;
import io.awspring.cloud.sqs.operations.SqsOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração para Amazon SQS (Spring Cloud AWS 4, AWS SDK v2).
 *
 * <p>Só existe com {@code sqs.enabled=true} (perfil {@code sqs}), assim como Kafka e RabbitMQ
 * dependem de {@code kafka.enabled} e {@code rabbitmq.enabled}. O cliente {@code SqsAsyncClient},
 * o {@code SqsTemplate} e a infraestrutura dos {@code @SqsListener} vêm da auto-configuração do
 * Spring Cloud AWS, a partir de {@code spring.cloud.aws.*} (região, credenciais, endpoint).
 */
@Configuration
@ConditionalOnProperty(name = "sqs.enabled", havingValue = "true")
public class SqsConfig {

    /**
     * Serviço que publica nas filas SQS.
     *
     * @param sqsOperations Template SQS auto-configurado
     * @return Produtor SQS
     */
    @Bean
    public SqsProducerService sqsProducerService(SqsOperations sqsOperations) {
        return new SqsProducerService(sqsOperations);
    }

    /**
     * Serviço com os {@code @SqsListener} que consomem as filas SQS.
     *
     * @return Consumidor SQS
     */
    @Bean
    public SqsConsumerService sqsConsumerService(MovimentoEstoqueRepository movimentoEstoqueRepository,
                                                 ProdutoRepository produtoRepository,
                                                 SqsProducerService sqsProducerService) {
        return new SqsConsumerService(movimentoEstoqueRepository, produtoRepository, sqsProducerService);
    }
}
