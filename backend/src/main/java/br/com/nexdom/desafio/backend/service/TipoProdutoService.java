package br.com.nexdom.desafio.backend.service;

import br.com.nexdom.desafio.backend.dto.TipoProdutoDTO;
import br.com.nexdom.desafio.backend.exception.ResourceNotFoundException;
import br.com.nexdom.desafio.backend.model.TipoProduto;
import br.com.nexdom.desafio.backend.repository.ProdutoRepository;
import br.com.nexdom.desafio.backend.repository.TipoProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço para operações relacionadas a TipoProduto.
 */
@Service
public class TipoProdutoService {

    private final TipoProdutoRepository tipoProdutoRepository;
    private final ProdutoRepository produtoRepository;

    @Autowired
    public TipoProdutoService(TipoProdutoRepository tipoProdutoRepository, ProdutoRepository produtoRepository) {
        this.tipoProdutoRepository = tipoProdutoRepository;
        this.produtoRepository = produtoRepository;
    }

    /**
     * Cria um novo tipo de produto.
     *
     * @param tipoProdutoDTO DTO com os dados do tipo de produto
     * @return DTO do tipo de produto criado
     */
    @Transactional
    public TipoProdutoDTO criar(TipoProdutoDTO tipoProdutoDTO) {
        TipoProduto tipoProduto = new TipoProduto();
        tipoProduto.setNome(tipoProdutoDTO.getNome());
        
        TipoProduto savedTipoProduto = tipoProdutoRepository.save(tipoProduto);
        
        return mapToDTO(savedTipoProduto);
    }

    /**
     * Busca todos os tipos de produto.
     *
     * @return Lista de DTOs dos tipos de produto
     */
    @Transactional(readOnly = true)
    public List<TipoProdutoDTO> buscarTodos() {
        List<TipoProduto> tiposProduto = tipoProdutoRepository.findAll();
        
        return tiposProduto.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca um tipo de produto pelo ID.
     *
     * @param id ID do tipo de produto
     * @return DTO do tipo de produto
     * @throws ResourceNotFoundException se o tipo de produto não for encontrado
     */
    @Transactional(readOnly = true)
    public TipoProdutoDTO buscarPorId(Long id) {
        TipoProduto tipoProduto = tipoProdutoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TipoProduto", "id", id));
        
        return mapToDTO(tipoProduto);
    }

    /**
     * Atualiza um tipo de produto.
     *
     * @param id ID do tipo de produto
     * @param tipoProdutoDTO DTO com os novos dados do tipo de produto
     * @return DTO do tipo de produto atualizado
     * @throws ResourceNotFoundException se o tipo de produto não for encontrado
     */
    @Transactional
    public TipoProdutoDTO atualizar(Long id, TipoProdutoDTO tipoProdutoDTO) {
        TipoProduto tipoProduto = tipoProdutoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TipoProduto", "id", id));
        
        tipoProduto.setNome(tipoProdutoDTO.getNome());
        
        TipoProduto updatedTipoProduto = tipoProdutoRepository.save(tipoProduto);
        
        return mapToDTO(updatedTipoProduto);
    }

    /**
     * Exclui um tipo de produto.
     *
     * @param id ID do tipo de produto
     * @throws ResourceNotFoundException se o tipo de produto não for encontrado
     * @throws DataIntegrityViolationException se houver produtos associados ao tipo de produto
     */
    @Transactional
    public void excluir(Long id) {
        TipoProduto tipoProduto = tipoProdutoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TipoProduto", "id", id));
        
        if (produtoRepository.existsByTipoProduto(tipoProduto)) {
            throw new DataIntegrityViolationException("Não é possível excluir um tipo de produto que possui produtos associados");
        }
        
        tipoProdutoRepository.delete(tipoProduto);
    }

    /**
     * Converte uma entidade TipoProduto para um DTO.
     *
     * @param tipoProduto Entidade TipoProduto
     * @return DTO do tipo de produto
     */
    private TipoProdutoDTO mapToDTO(TipoProduto tipoProduto) {
        TipoProdutoDTO tipoProdutoDTO = new TipoProdutoDTO();
        tipoProdutoDTO.setId(tipoProduto.getId());
        tipoProdutoDTO.setNome(tipoProduto.getNome());
        
        return tipoProdutoDTO;
    }
}