package br.com.nexdom.desafio.backend.service;

import br.com.nexdom.desafio.backend.dto.MovimentoEstoqueMessageDTO;
import br.com.nexdom.desafio.backend.exception.EstoqueInsuficienteException;
import br.com.nexdom.desafio.backend.exception.ResourceNotFoundException;
import br.com.nexdom.desafio.backend.model.MovimentoEstoque;
import br.com.nexdom.desafio.backend.model.Produto;
import br.com.nexdom.desafio.backend.model.enums.TipoMovimentacao;
import br.com.nexdom.desafio.backend.repository.MovimentoEstoqueRepository;
import br.com.nexdom.desafio.backend.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.aws.messaging.listener.annotation.SqsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Serviço responsável por consumir mensagens das filas Amazon SQS.
 * Processa operações assíncronas relacionadas ao controle de estoque.
 */
@Slf4j
@Service
public class SqsConsumerService {

    private final MovimentoEstoqueRepository movimentoEstoqueRepository;
    private final ProdutoRepository produtoRepository;
    private final SqsProducerService sqsProducerService;

    @Autowired
    public SqsConsumerService(MovimentoEstoqueRepository movimentoEstoqueRepository,
                             ProdutoRepository produtoRepository,
                             SqsProducerService sqsProducerService) {
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
        this.produtoRepository = produtoRepository;
        this.sqsProducerService = sqsProducerService;
    }

    /**
     * Processa mensagens de movimento de estoque da fila SQS.
     *
     * @param message Mensagem com dados do movimento
     */
    @SqsListener("${sqs.queue.movimento-estoque}")
    @Transactional
    public void processarMovimentoEstoque(@Payload MovimentoEstoqueMessageDTO message) {
        log.info("Processando movimento de estoque assíncrono. OperationId: {}, Produto: {}, Tipo: {}", 
                message.getOperationId(), message.getProdutoId(), message.getTipoMovimentacao());

        try {
            // Incrementa contador de tentativas
            message.setTentativas(message.getTentativas() + 1);
            
            // Busca o produto
            Produto produto = produtoRepository.findById(message.getProdutoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto", "id", message.getProdutoId()));

            // Cria o movimento de estoque
            MovimentoEstoque movimento = new MovimentoEstoque();
            movimento.setDataMovimento(LocalDateTime.now());
            movimento.setTipoMovimentacao(message.getTipoMovimentacao());
            movimento.setQuantidadeMovimentada(message.getQuantidadeMovimentada());
            movimento.setProduto(produto);

            // Processa conforme o tipo de movimentação
            if (message.getTipoMovimentacao() == TipoMovimentacao.ENTRADA) {
                processarEntrada(produto, movimento, message);
            } else if (message.getTipoMovimentacao() == TipoMovimentacao.SAIDA) {
                processarSaida(produto, movimento, message);
            }

            // Salva o produto atualizado
            produtoRepository.save(produto);

            // Salva o movimento
            movimentoEstoqueRepository.save(movimento);

            // Envia auditoria
            sqsProducerService.enviarAuditoria(
                    "MOVIMENTO_PROCESSADO",
                    "MovimentoEstoque",
                    movimento.getId(),
                    message.getUsuarioId(),
                    String.format("Movimento %s processado com sucesso. Produto: %d, Quantidade: %d", 
                            message.getTipoMovimentacao(), message.getProdutoId(), message.getQuantidadeMovimentada())
            );

            log.info("Movimento de estoque processado com sucesso. OperationId: {}, MovimentoId: {}", 
                    message.getOperationId(), movimento.getId());

        } catch (EstoqueInsuficienteException e) {
            log.error("Estoque insuficiente para processar movimento. OperationId: {}, Erro: {}", 
                    message.getOperationId(), e.getMessage());
            
            message.setMotivoErro("Estoque insuficiente: " + e.getMessage());
            enviarParaDLQ(message);
            
        } catch (ResourceNotFoundException e) {
            log.error("Produto não encontrado para processar movimento. OperationId: {}, Erro: {}", 
                    message.getOperationId(), e.getMessage());
            
            message.setMotivoErro("Produto não encontrado: " + e.getMessage());
            enviarParaDLQ(message);
            
        } catch (Exception e) {
            log.error("Erro inesperado ao processar movimento de estoque. OperationId: {}, Tentativas: {}, Erro: {}", 
                    message.getOperationId(), message.getTentativas(), e.getMessage(), e);
            
            // Rejeita a mensagem se excedeu o número de tentativas
            if (message.getTentativas() >= 3) {
                message.setMotivoErro("Número máximo de tentativas excedido: " + e.getMessage());
                enviarParaDLQ(message);
            } else {
                throw new RuntimeException("Erro temporário no processamento", e);
            }
        }
    }

    /**
     * Processa movimento de entrada de estoque.
     */
    private void processarEntrada(Produto produto, MovimentoEstoque movimento, MovimentoEstoqueMessageDTO message) {
        // Incrementa o estoque
        produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() + message.getQuantidadeMovimentada());
        movimento.setValorVenda(null);
        
        log.debug("Entrada processada. Produto: {}, Nova quantidade: {}", 
                produto.getId(), produto.getQuantidadeEmEstoque());
    }

    /**
     * Processa movimento de saída de estoque.
     */
    private void processarSaida(Produto produto, MovimentoEstoque movimento, MovimentoEstoqueMessageDTO message) {
        // Verifica se há estoque suficiente
        if (produto.getQuantidadeEmEstoque() < message.getQuantidadeMovimentada()) {
            throw new EstoqueInsuficienteException(
                    produto.getId(),
                    produto.getQuantidadeEmEstoque(),
                    message.getQuantidadeMovimentada()
            );
        }

        // Decrementa o estoque
        produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() - message.getQuantidadeMovimentada());

        // Calcula o valor de venda
        BigDecimal valorVenda = produto.getValorFornecedor()
                .multiply(new BigDecimal("1.35"))
                .setScale(2, RoundingMode.HALF_UP);
        movimento.setValorVenda(valorVenda);

        // Verifica se precisa notificar estoque baixo (exemplo: menos de 10 unidades)
        if (produto.getQuantidadeEmEstoque() < 10) {
            sqsProducerService.enviarNotificacaoEstoqueBaixo(
                    produto.getId(),
                    produto.getQuantidadeEmEstoque(),
                    10
            );
        }

        log.debug("Saída processada. Produto: {}, Nova quantidade: {}, Valor venda: {}", 
                produto.getId(), produto.getQuantidadeEmEstoque(), valorVenda);
    }

    /**
     * Processa notificações de estoque baixo.
     *
     * @param payload Conteúdo da mensagem
     * @param produtoId ID do produto
     * @param tipo Tipo de notificação
     */
    @SqsListener("${sqs.queue.notificacao-estoque}")
    public void processarNotificacaoEstoque(@Payload String payload,
                                          @Header("produtoId") Long produtoId,
                                          @Header("tipo") String tipo) {
        log.info("Processando notificação de estoque. Tipo: {}, Produto: {}, Mensagem: {}", 
                tipo, produtoId, payload);

        try {
            // Aqui você pode implementar diferentes tipos de notificação:
            // - Envio de email
            // - Notificação push
            // - Integração com sistemas externos
            // - Criação de alertas no dashboard
            
            switch (tipo) {
                case "ESTOQUE_BAIXO":
                    log.warn("ALERTA: Estoque baixo detectado - {}", payload);
                    // Implementar lógica de notificação
                    break;
                case "PRODUTO_ESGOTADO":
                    log.error("CRÍTICO: Produto esgotado - {}", payload);
                    // Implementar lógica de notificação crítica
                    break;
                default:
                    log.info("Notificação genérica: {}", payload);
            }

        } catch (Exception e) {
            log.error("Erro ao processar notificação de estoque. Produto: {}, Erro: {}", 
                    produtoId, e.getMessage(), e);
        }
    }

    /**
     * Processa mensagens de auditoria.
     *
     * @param payload Detalhes da operação
     * @param operacao Tipo de operação
     * @param entidade Entidade afetada
     * @param entidadeId ID da entidade
     * @param usuarioId ID do usuário
     */
    @SqsListener("${sqs.queue.auditoria}")
    public void processarAuditoria(@Payload String payload,
                                 @Header("operacao") String operacao,
                                 @Header("entidade") String entidade,
                                 @Header("entidadeId") Long entidadeId,
                                 @Header("usuarioId") String usuarioId) {
        log.info("Processando auditoria. Operação: {}, Entidade: {}, ID: {}, Usuário: {}", 
                operacao, entidade, entidadeId, usuarioId);

        try {
            // Aqui você pode implementar:
            // - Persistência em banco de auditoria
            // - Envio para sistemas de compliance
            // - Integração com ferramentas de monitoramento
            // - Geração de relatórios de auditoria
            
            log.debug("Detalhes da auditoria: {}", payload);
            
        } catch (Exception e) {
            log.error("Erro ao processar auditoria. Operação: {}, Entidade: {}, ID: {}, Erro: {}", 
                    operacao, entidade, entidadeId, e.getMessage(), e);
        }
    }

    /**
     * Envia mensagem para Dead Letter Queue em caso de falha.
     */
    private void enviarParaDLQ(MovimentoEstoqueMessageDTO message) {
        log.error("Enviando mensagem para DLQ. OperationId: {}, Motivo: {}", 
                message.getOperationId(), message.getMotivoErro());
        
        // Aqui você implementaria o envio para DLQ
        // Por simplicidade, apenas logamos o erro
        // Em produção, você enviaria para uma fila DLQ específica
    }
} 