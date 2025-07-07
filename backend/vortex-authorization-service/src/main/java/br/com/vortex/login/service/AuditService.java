package br.com.vortex.login.service;

import br.com.vortex.login.dto.AuditoriaEventDTO;
import br.com.vortex.login.dto.UserDTO;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.logging.Logger;

@ApplicationScoped
public class AuditService {
    
    private static final Logger log = Logger.getLogger(AuditService.class.getName());
    
    @Channel("auditoria")
    Emitter<AuditoriaEventDTO> auditoriaEmitter;
    
    public void publishLoginEvent(UserDTO user, String action, String status) {
        try {
            AuditoriaEventDTO event = new AuditoriaEventDTO();
            event.setAcao(action);
            event.setEntidade("USER");
            event.setEntidadeId(user != null ? user.getId() : null);
            event.setUserId(user != null ? user.getEmail() : "unknown");
            event.setResultado(status);
            event.setDetalhes(user != null ? "Login via " + user.getProvider() : "Login failed");
            
            auditoriaEmitter.send(event);
            log.fine("Evento de auditoria publicado: " + action);
        } catch (Exception e) {
            log.severe("Erro ao publicar evento de auditoria: " + e.getMessage());
        }
    }
    
    public void publishUserEvent(UserDTO user, String action, String status) {
        try {
            AuditoriaEventDTO event = new AuditoriaEventDTO();
            event.setAcao(action);
            event.setEntidade("USER");
            event.setEntidadeId(user.getId());
            event.setUserId(user.getEmail());
            event.setResultado(status);
            event.setDetalhes("User " + action.toLowerCase() + " - Provider: " + user.getProvider());
            
            auditoriaEmitter.send(event);
            log.fine("Evento de usuário publicado: " + action);
        } catch (Exception e) {
            log.severe("Erro ao publicar evento de usuário: " + e.getMessage());
        }
    }
    
    public void publishPasswordResetEvent(String email, String action, String status) {
        try {
            AuditoriaEventDTO event = new AuditoriaEventDTO();
            event.setAcao(action);
            event.setEntidade("PASSWORD_RESET");
            event.setUserId(email != null ? email : "unknown");
            event.setResultado(status);
            event.setDetalhes("Password reset operation");
            
            auditoriaEmitter.send(event);
            log.fine("Evento de reset de senha publicado: " + action);
        } catch (Exception e) {
            log.severe("Erro ao publicar evento de reset de senha: " + e.getMessage());
        }
    }
}