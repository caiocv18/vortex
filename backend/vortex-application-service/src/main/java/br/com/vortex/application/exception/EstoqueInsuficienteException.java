package br.com.vortex.application.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exceção lançada quando não há estoque suficiente para uma movimentação.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class EstoqueInsuficienteException extends RuntimeException {

    public EstoqueInsuficienteException(String message) {
        super(message);
    }

    public EstoqueInsuficienteException(Long produtoId, Integer quantidadeDisponivel, Integer quantidadeSolicitada) {
        super(String.format("Estoque insuficiente para o produto ID %d. Disponível: %d, Solicitado: %d", 
                produtoId, quantidadeDisponivel, quantidadeSolicitada));
    }
}