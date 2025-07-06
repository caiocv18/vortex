package br.com.vortex.desafio.backend.controller;

import br.com.vortex.desafio.backend.dto.QueueInfoDTO;
import br.com.vortex.desafio.backend.service.QueueMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller responsável por fornecer informações sobre filas RabbitMQ ao frontend
 * 
 * Este controller permite:
 * - Listar todas as filas configuradas
 * - Obter informações detalhadas sobre uma fila específica
 * - Monitorar o status das filas
 * - Buscar mensagens das filas para diagnóstico
 */
@Slf4j
@RestController
@RequestMapping("/api/queues")
@CrossOrigin(origins = "*")
public class QueueController {

    @Autowired
    private QueueMonitoringService queueMonitoringService;

    /**
     * Lista todas as filas configuradas no sistema.
     *
     * @return Lista de informações das filas
     */
    @GetMapping
    public ResponseEntity<List<QueueInfoDTO>> listarFilas() {
        log.info("Solicitação para listar todas as filas");
        try {
            List<QueueInfoDTO> filas = queueMonitoringService.listarFilas();
            log.info("Retornando {} filas", filas.size());
            return ResponseEntity.ok(filas);
        } catch (Exception e) {
            log.error("Erro ao listar filas", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtém informações detalhadas sobre uma fila específica.
     *
     * @param nomeFila Nome da fila
     * @return Informações da fila
     */
    @GetMapping("/{nomeFila}")
    public ResponseEntity<QueueInfoDTO> obterInformacoesFila(@PathVariable String nomeFila) {
        log.info("Solicitação para obter informações da fila: {}", nomeFila);
        try {
            QueueInfoDTO fila = queueMonitoringService.obterInformacoesFila(nomeFila);
            if (fila != null) {
                return ResponseEntity.ok(fila);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Erro ao obter informações da fila: {}", nomeFila, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Obtém o status geral do sistema de mensageria.
     *
     * @return Status do sistema
     */
    @GetMapping("/status")
    public ResponseEntity<Object> obterStatusSistema() {
        log.info("Solicitação para obter status do sistema de mensageria");
        try {
            Object status = queueMonitoringService.obterStatusSistema();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Erro ao obter status do sistema", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Busca mensagens de uma fila específica para diagnóstico.
     * 
     * @param nomeFila Nome da fila
     * @param quantidade Quantidade máxima de mensagens a buscar (padrão: 10)
     * @return Lista de mensagens ou informação sobre fila vazia
     */
    @GetMapping("/{nomeFila}/messages")
    public ResponseEntity<Map<String, Object>> buscarMensagens(
            @PathVariable String nomeFila,
            @RequestParam(defaultValue = "10") int quantidade) {
        log.info("Solicitação para buscar mensagens da fila: {} (quantidade: {})", nomeFila, quantidade);
        try {
            Map<String, Object> resultado = queueMonitoringService.buscarMensagens(nomeFila, quantidade);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            log.error("Erro ao buscar mensagens da fila: {}", nomeFila, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Publica uma mensagem de teste em uma fila específica.
     * 
     * @param nomeFila Nome da fila
     * @param mensagem Mensagem a ser publicada
     * @return Resultado da operação
     */
    @PostMapping("/{nomeFila}/test-message")
    public ResponseEntity<Map<String, Object>> publicarMensagemTeste(
            @PathVariable String nomeFila,
            @RequestBody Map<String, Object> mensagem) {
        log.info("Solicitação para publicar mensagem de teste na fila: {}", nomeFila);
        try {
            Map<String, Object> resultado = queueMonitoringService.publicarMensagemTeste(nomeFila, mensagem);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            log.error("Erro ao publicar mensagem de teste na fila: {}", nomeFila, e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 