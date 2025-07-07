package br.com.vortex.login.dto;

import java.time.LocalDateTime;

public class AuditoriaEventDTO {
    private String acao;
    private String entidade;
    private Long entidadeId;
    private String userId;
    private String resultado;
    private String detalhes;
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public String getAcao() {
        return acao;
    }
    
    public void setAcao(String acao) {
        this.acao = acao;
    }
    
    public String getEntidade() {
        return entidade;
    }
    
    public void setEntidade(String entidade) {
        this.entidade = entidade;
    }
    
    public Long getEntidadeId() {
        return entidadeId;
    }
    
    public void setEntidadeId(Long entidadeId) {
        this.entidadeId = entidadeId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getResultado() {
        return resultado;
    }
    
    public void setResultado(String resultado) {
        this.resultado = resultado;
    }
    
    public String getDetalhes() {
        return detalhes;
    }
    
    public void setDetalhes(String detalhes) {
        this.detalhes = detalhes;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}