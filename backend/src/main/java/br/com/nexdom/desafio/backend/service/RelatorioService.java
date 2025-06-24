package br.com.nexdom.desafio.backend.service;

import br.com.nexdom.desafio.backend.dto.LucroPorProdutoDTO;
import br.com.nexdom.desafio.backend.dto.ProdutoPorTipoDTO;
import br.com.nexdom.desafio.backend.exception.ResourceNotFoundException;
import br.com.nexdom.desafio.backend.model.Produto;
import br.com.nexdom.desafio.backend.model.TipoProduto;
import br.com.nexdom.desafio.backend.model.enums.TipoMovimentacao;
import br.com.nexdom.desafio.backend.repository.MovimentoEstoqueRepository;
import br.com.nexdom.desafio.backend.repository.ProdutoRepository;
import br.com.nexdom.desafio.backend.repository.TipoProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço para operações relacionadas a relatórios.
 */
@Service
public class RelatorioService {

    private final ProdutoRepository produtoRepository;
    private final TipoProdutoRepository tipoProdutoRepository;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;

    @Autowired
    public RelatorioService(ProdutoRepository produtoRepository,
                           TipoProdutoRepository tipoProdutoRepository,
                           MovimentoEstoqueRepository movimentoEstoqueRepository) {
        this.produtoRepository = produtoRepository;
        this.tipoProdutoRepository = tipoProdutoRepository;
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
    }

    /**
     * Gera relatório de produtos por tipo.
     *
     * @param tipoProdutoId ID do tipo de produto
     * @return Lista de DTOs com informações dos produtos do tipo especificado
     * @throws ResourceNotFoundException se o tipo de produto não for encontrado
     */
    @Transactional(readOnly = true)
    public List<ProdutoPorTipoDTO> gerarRelatorioProdutosPorTipo(Long tipoProdutoId) {
        TipoProduto tipoProduto = tipoProdutoRepository.findById(tipoProdutoId)
                .orElseThrow(() -> new ResourceNotFoundException("TipoProduto", "id", tipoProdutoId));
        
        List<Produto> produtos = produtoRepository.findByTipoProduto(tipoProduto);
        
        return produtos.stream()
                .map(produto -> {
                    ProdutoPorTipoDTO dto = new ProdutoPorTipoDTO();
                    dto.setId(produto.getId());
                    dto.setDescricao(produto.getDescricao());
                    dto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque());
                    
                    // Calcula o total de saídas para o produto
                    long totalSaidas = movimentoEstoqueRepository.countByProdutoAndTipoMovimentacao(
                            produto, TipoMovimentacao.SAIDA);
                    dto.setTotalSaidas((int) totalSaidas);
                    
                    return dto;
                })
                .toList();
    }

    /**
     * Gera relatório de lucro por produto.
     *
     * @return Lista de DTOs com informações de lucro por produto
     */
    @Transactional(readOnly = true)
    public List<LucroPorProdutoDTO> gerarRelatorioLucroPorProduto() {
        List<Produto> produtos = produtoRepository.findAll();
        List<LucroPorProdutoDTO> resultado = new ArrayList<>();
        
        for (Produto produto : produtos) {
            LucroPorProdutoDTO dto = new LucroPorProdutoDTO();
            dto.setId(produto.getId());
            dto.setDescricao(produto.getDescricao());
            
            // Busca o total de unidades vendidas
            Integer totalUnidadesVendidas = movimentoEstoqueRepository.getTotalUnidadesVendidas(produto.getId());
            dto.setTotalUnidadesVendidas(totalUnidadesVendidas != null ? totalUnidadesVendidas : 0);
            
            // Busca o lucro total
            BigDecimal lucroTotal = movimentoEstoqueRepository.getLucroTotal(produto.getId());
            dto.setLucroTotal(lucroTotal != null ? lucroTotal : BigDecimal.ZERO);
            
            resultado.add(dto);
        }
        
        return resultado;
    }
}