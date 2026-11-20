package br.com.vortex.application.service;

import br.com.vortex.application.config.BusinessTime;
import br.com.vortex.application.dto.MovimentoEstoqueDTO;
import br.com.vortex.application.dto.MovimentoEstoqueMessageDTO;
import br.com.vortex.application.exception.EstoqueInsuficienteException;
import br.com.vortex.application.exception.ResourceNotFoundException;
import br.com.vortex.application.model.MovimentoEstoque;
import br.com.vortex.application.model.Produto;
import br.com.vortex.application.model.enums.TipoMovimentacao;
import br.com.vortex.application.repository.MovimentoEstoqueRepository;
import br.com.vortex.application.repository.ProdutoRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import static br.com.vortex.application.repository.MovimentoEstoqueSpecifications.comDescricaoDoProdutoContendo;
import static br.com.vortex.application.repository.MovimentoEstoqueSpecifications.doTipo;

/**
 * Serviço para operações relacionadas a MovimentoEstoque.
 */
@Service
public class MovimentoEstoqueService {

    private static final String MOVIMENTO_ESTOQUE = "MovimentoEstoque";
    private static final String PRODUTO = "Produto";

    private final MovimentoEstoqueRepository movimentoEstoqueRepository;
    private final ProdutoRepository produtoRepository;
    // Nulo quando o SQS está desligado (sqs.enabled=false)
    private final SqsProducerService sqsProducerService;
    private final MessageBrokerService messageBrokerService;
    private final boolean processamentoAssincronoEnabled;

    public MovimentoEstoqueService(MovimentoEstoqueRepository movimentoEstoqueRepository,
                                  ProdutoRepository produtoRepository,
                                  ObjectProvider<SqsProducerService> sqsProducerService,
                                  MessageBrokerService messageBrokerService,
                                  @Value("${sqs.processamento.assincrono.enabled:false}") boolean processamentoAssincronoEnabled) {
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
        this.produtoRepository = produtoRepository;
        this.sqsProducerService = sqsProducerService.getIfAvailable();
        this.messageBrokerService = messageBrokerService;
        this.processamentoAssincronoEnabled = processamentoAssincronoEnabled;
        if (processamentoAssincronoEnabled && this.sqsProducerService == null) {
            throw new IllegalStateException(
                    "sqs.processamento.assincrono.enabled=true exige o SQS ligado (sqs.enabled=true, perfil sqs)");
        }
    }

    /**
     * Cria um novo movimento de estoque, de forma síncrona ou pela fila SQS conforme a configuração.
     *
     * @param movimentoEstoqueDTO DTO com os dados do movimento de estoque
     * @param usuarioId ID do usuário que está realizando a operação
     * @return DTO do movimento de estoque criado
     * @throws ResourceNotFoundException se o produto não for encontrado
     * @throws EstoqueInsuficienteException se não houver estoque suficiente para uma saída
     */
    @Transactional
    public MovimentoEstoqueDTO criar(MovimentoEstoqueDTO movimentoEstoqueDTO, String usuarioId) {
        // Se processamento assíncrono estiver habilitado, envia para SQS
        if (processamentoAssincronoEnabled) {
            return criarAssincrono(movimentoEstoqueDTO, usuarioId);
        }
        
        // Processamento síncrono (comportamento original)
        return criarSincrono(movimentoEstoqueDTO, usuarioId);
    }

    /**
     * Cria movimento de estoque de forma assíncrona via SQS.
     */
    private MovimentoEstoqueDTO criarAssincrono(MovimentoEstoqueDTO movimentoEstoqueDTO, String usuarioId) {
        // Valida se o produto existe antes de enviar para a fila
        Produto produto = produtoRepository.findById(movimentoEstoqueDTO.getProdutoId())
                .orElseThrow(() -> new ResourceNotFoundException(PRODUTO, "id", movimentoEstoqueDTO.getProdutoId()));

        // Cria mensagem para SQS
        MovimentoEstoqueMessageDTO message = new MovimentoEstoqueMessageDTO();
        message.setTipoMovimentacao(movimentoEstoqueDTO.getTipoMovimentacao());
        message.setQuantidadeMovimentada(movimentoEstoqueDTO.getQuantidadeMovimentada());
        message.setProdutoId(movimentoEstoqueDTO.getProdutoId());
        message.setValorFornecedor(produto.getValorFornecedor());
        message.setUsuarioId(usuarioId);
        message.setPrioridade("NORMAL");

        // Envia para SQS
        sqsProducerService.enviarMovimentoEstoque(message);

        // Retorna DTO com informações básicas (sem ID do movimento, pois será processado assincronamente)
        MovimentoEstoqueDTO responseDTO = new MovimentoEstoqueDTO();
        responseDTO.setTipoMovimentacao(movimentoEstoqueDTO.getTipoMovimentacao());
        responseDTO.setQuantidadeMovimentada(movimentoEstoqueDTO.getQuantidadeMovimentada());
        responseDTO.setProdutoId(movimentoEstoqueDTO.getProdutoId());
        responseDTO.setProdutoDescricao(produto.getDescricao());
        responseDTO.setDataMovimento(LocalDateTime.now(BusinessTime.ZONE));

        // Envia auditoria
        sqsProducerService.enviarAuditoria(
                "MOVIMENTO_ENVIADO_SQS",
                MOVIMENTO_ESTOQUE,
                null,
                usuarioId,
                String.format("Movimento %s enviado para processamento assíncrono. Produto: %d, Quantidade: %d, OperationId: %s", 
                        movimentoEstoqueDTO.getTipoMovimentacao(), 
                        movimentoEstoqueDTO.getProdutoId(), 
                        movimentoEstoqueDTO.getQuantidadeMovimentada(),
                        message.getOperationId())
        );

        return responseDTO;
    }

    /**
     * Cria movimento de estoque de forma síncrona (comportamento original).
     */
    private MovimentoEstoqueDTO criarSincrono(MovimentoEstoqueDTO movimentoEstoqueDTO, String usuarioId) {
        Produto produto = produtoRepository.findById(movimentoEstoqueDTO.getProdutoId())
                .orElseThrow(() -> new ResourceNotFoundException(PRODUTO, "id", movimentoEstoqueDTO.getProdutoId()));
        
        // Armazenar estoque anterior para o evento Kafka
        Integer estoqueAnterior = produto.getQuantidadeEmEstoque();
        
        MovimentoEstoque movimentoEstoque = new MovimentoEstoque();
        movimentoEstoque.setDataMovimento(LocalDateTime.now(BusinessTime.ZONE));
        movimentoEstoque.setTipoMovimentacao(movimentoEstoqueDTO.getTipoMovimentacao());
        movimentoEstoque.setQuantidadeMovimentada(movimentoEstoqueDTO.getQuantidadeMovimentada());
        movimentoEstoque.setProduto(produto);
        
        // Lógica específica para cada tipo de movimentação
        if (movimentoEstoqueDTO.getTipoMovimentacao() == TipoMovimentacao.ENTRADA) {
            // Para ENTRADA, incrementa o estoque e não define valor de venda
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() + movimentoEstoqueDTO.getQuantidadeMovimentada());
            movimentoEstoque.setValorVenda(null);
        } else if (movimentoEstoqueDTO.getTipoMovimentacao() == TipoMovimentacao.SAIDA) {
            // Para SAIDA, verifica se há estoque suficiente
            if (produto.getQuantidadeEmEstoque() < movimentoEstoqueDTO.getQuantidadeMovimentada()) {
                throw new EstoqueInsuficienteException(
                        produto.getId(), 
                        produto.getQuantidadeEmEstoque(), 
                        movimentoEstoqueDTO.getQuantidadeMovimentada());
            }
            
            // Decrementa o estoque
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() - movimentoEstoqueDTO.getQuantidadeMovimentada());
            
            // Calcula o valor de venda como valorFornecedor * 1.35
            BigDecimal valorVenda = produto.getValorFornecedor()
                    .multiply(new BigDecimal("1.35"))
                    .setScale(2, RoundingMode.HALF_UP);
            
            movimentoEstoque.setValorVenda(valorVenda);
        }
        
        // Salva o produto atualizado
        produtoRepository.save(produto);
        
        // Salva o movimento de estoque
        MovimentoEstoque savedMovimento = movimentoEstoqueRepository.save(movimentoEstoque);
        
        // INTEGRAÇÃO KAFKA: Publica evento de movimentação
        if (messageBrokerService.isAvailable()) {
            messageBrokerService.publicarMovimentoEstoque(savedMovimento, produto, estoqueAnterior, usuarioId);
            
            // Verificar se precisa gerar alertas de estoque
            verificarAlertas(produto, usuarioId);
            
            // Auditoria via Message Broker
            messageBrokerService.publicarAuditoria(
                "MOVIMENTO_CRIADO",
                MOVIMENTO_ESTOQUE,
                savedMovimento.getId(), 
                String.format("Movimento %s criado. Produto: %d, Quantidade: %d", 
                    savedMovimento.getTipoMovimentacao(), 
                    produto.getId(), 
                    savedMovimento.getQuantidadeMovimentada()),
                usuarioId, 
                "SUCCESS", 
                null
            );
        }
        
        return mapToDTO(savedMovimento);
    }
    
    /**
     * Verifica se é necessário gerar alertas de estoque após movimentação.
     */
    private void verificarAlertas(Produto produto, String usuarioId) {
        Integer quantidadeAtual = produto.getQuantidadeEmEstoque();
        
        if (quantidadeAtual <= 0) {
            messageBrokerService.publicarAlertaEstoqueEsgotado(produto, usuarioId);
        } else if (quantidadeAtual <= 5) {
            messageBrokerService.publicarAlertaEstoqueCritico(produto, 5, usuarioId);
        } else if (quantidadeAtual <= 10) {
            messageBrokerService.publicarAlertaEstoqueBaixo(produto, 10, usuarioId);
        }
    }

    /**
     * Busca uma página de movimentos de estoque, com filtros opcionais pela descrição do produto e pelo tipo.
     *
     * @param q trecho da descrição do produto (contém, sem diferenciar maiúsculas); nulo ou em branco não filtra
     * @param tipo tipo de movimentação; nulo traz entradas e saídas
     * @param pageable página, tamanho e ordenação
     * @return Página de DTOs dos movimentos de estoque
     */
    @Transactional(readOnly = true)
    public Page<MovimentoEstoqueDTO> buscar(String q, TipoMovimentacao tipo, Pageable pageable) {
        Specification<MovimentoEstoque> filtro = Specification.unrestricted();
        if (tipo != null) {
            filtro = filtro.and(doTipo(tipo));
        }
        String trecho = q == null ? "" : q.strip();
        if (!trecho.isEmpty()) {
            filtro = filtro.and(comDescricaoDoProdutoContendo(trecho));
        }

        return movimentoEstoqueRepository.findAll(filtro, pageable).map(this::mapToDTO);
    }

    /**
     * Busca um movimento de estoque pelo ID.
     *
     * @param id ID do movimento de estoque
     * @return DTO do movimento de estoque
     * @throws ResourceNotFoundException se o movimento de estoque não for encontrado
     */
    @Transactional(readOnly = true)
    public MovimentoEstoqueDTO buscarPorId(Long id) {
        MovimentoEstoque movimentoEstoque = movimentoEstoqueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(MOVIMENTO_ESTOQUE, "id", id));
        
        return mapToDTO(movimentoEstoque);
    }

    /**
     * Atualiza um movimento de estoque.
     * Atenção: Esta operação é complexa e deve garantir a consistência do estoque.
     *
     * @param id ID do movimento de estoque
     * @param movimentoEstoqueDTO DTO com os novos dados do movimento de estoque
     * @return DTO do movimento de estoque atualizado
     * @throws ResourceNotFoundException se o movimento de estoque ou o produto não for encontrado
     * @throws EstoqueInsuficienteException se não houver estoque suficiente para uma saída
     */
    @Transactional
    public MovimentoEstoqueDTO atualizar(Long id, MovimentoEstoqueDTO movimentoEstoqueDTO) {
        MovimentoEstoque movimentoEstoque = movimentoEstoqueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(MOVIMENTO_ESTOQUE, "id", id));
        
        Produto produto = produtoRepository.findById(movimentoEstoqueDTO.getProdutoId())
                .orElseThrow(() -> new ResourceNotFoundException(PRODUTO, "id", movimentoEstoqueDTO.getProdutoId()));
        
        // Reverte o efeito do movimento original no estoque
        if (movimentoEstoque.getTipoMovimentacao() == TipoMovimentacao.ENTRADA) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() - movimentoEstoque.getQuantidadeMovimentada());
        } else if (movimentoEstoque.getTipoMovimentacao() == TipoMovimentacao.SAIDA) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() + movimentoEstoque.getQuantidadeMovimentada());
        }
        
        // Aplica o efeito do novo movimento no estoque
        if (movimentoEstoqueDTO.getTipoMovimentacao() == TipoMovimentacao.ENTRADA) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() + movimentoEstoqueDTO.getQuantidadeMovimentada());
            movimentoEstoque.setValorVenda(null);
        } else if (movimentoEstoqueDTO.getTipoMovimentacao() == TipoMovimentacao.SAIDA) {
            // Verifica se há estoque suficiente
            if (produto.getQuantidadeEmEstoque() < movimentoEstoqueDTO.getQuantidadeMovimentada()) {
                throw new EstoqueInsuficienteException(
                        produto.getId(), 
                        produto.getQuantidadeEmEstoque(), 
                        movimentoEstoqueDTO.getQuantidadeMovimentada());
            }
            
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() - movimentoEstoqueDTO.getQuantidadeMovimentada());
            
            // Calcula o valor de venda como valorFornecedor * 1.35
            BigDecimal valorVenda = produto.getValorFornecedor()
                    .multiply(new BigDecimal("1.35"))
                    .setScale(2, RoundingMode.HALF_UP);
            
            movimentoEstoque.setValorVenda(valorVenda);
        }
        
        // Atualiza os dados do movimento
        movimentoEstoque.setTipoMovimentacao(movimentoEstoqueDTO.getTipoMovimentacao());
        movimentoEstoque.setQuantidadeMovimentada(movimentoEstoqueDTO.getQuantidadeMovimentada());
        movimentoEstoque.setProduto(produto);
        
        // Salva o produto atualizado
        produtoRepository.save(produto);
        
        // Salva o movimento de estoque
        MovimentoEstoque updatedMovimento = movimentoEstoqueRepository.save(movimentoEstoque);
        
        return mapToDTO(updatedMovimento);
    }

    /**
     * Exclui um movimento de estoque.
     * Atenção: Esta operação é complexa e deve garantir a consistência do estoque.
     *
     * @param id ID do movimento de estoque
     * @throws ResourceNotFoundException se o movimento de estoque não for encontrado
     */
    @Transactional
    public void excluir(Long id) {
        MovimentoEstoque movimentoEstoque = movimentoEstoqueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(MOVIMENTO_ESTOQUE, "id", id));
        
        Produto produto = movimentoEstoque.getProduto();
        
        // Reverte o efeito do movimento no estoque
        if (movimentoEstoque.getTipoMovimentacao() == TipoMovimentacao.ENTRADA) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() - movimentoEstoque.getQuantidadeMovimentada());
        } else if (movimentoEstoque.getTipoMovimentacao() == TipoMovimentacao.SAIDA) {
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() + movimentoEstoque.getQuantidadeMovimentada());
        }
        
        // Salva o produto atualizado
        produtoRepository.save(produto);
        
        // Exclui o movimento de estoque
        movimentoEstoqueRepository.delete(movimentoEstoque);
    }

    /**
     * Converte uma entidade MovimentoEstoque para um DTO.
     *
     * @param movimentoEstoque Entidade MovimentoEstoque
     * @return DTO do movimento de estoque
     */
    private MovimentoEstoqueDTO mapToDTO(MovimentoEstoque movimentoEstoque) {
        MovimentoEstoqueDTO movimentoEstoqueDTO = new MovimentoEstoqueDTO();
        movimentoEstoqueDTO.setId(movimentoEstoque.getId());
        movimentoEstoqueDTO.setDataMovimento(movimentoEstoque.getDataMovimento());
        movimentoEstoqueDTO.setTipoMovimentacao(movimentoEstoque.getTipoMovimentacao());
        movimentoEstoqueDTO.setQuantidadeMovimentada(movimentoEstoque.getQuantidadeMovimentada());
        movimentoEstoqueDTO.setValorVenda(movimentoEstoque.getValorVenda());
        movimentoEstoqueDTO.setProdutoId(movimentoEstoque.getProduto().getId());
        movimentoEstoqueDTO.setProdutoDescricao(movimentoEstoque.getProduto().getDescricao());

        return movimentoEstoqueDTO;
    }
}
