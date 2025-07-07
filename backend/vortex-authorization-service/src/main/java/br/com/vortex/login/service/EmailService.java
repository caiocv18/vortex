package br.com.vortex.login.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.logging.Logger;

@ApplicationScoped
public class EmailService {
    
    private static final Logger log = Logger.getLogger(EmailService.class.getName());
    
    @Inject
    Mailer mailer;
    
    @ConfigProperty(name = "auth.password-reset.base-url")
    String baseUrl;
    
    public void sendPasswordResetEmail(String email, String name, String resetToken) {
        try {
            String resetLink = baseUrl + "/reset-password?token=" + resetToken;
            
            String htmlContent = buildPasswordResetEmailTemplate(name, resetLink);
            
            mailer.send(Mail.withHtml(email, "Redefinição de Senha - VORTEX", htmlContent));
            
            log.info("Email de recuperação enviado para: " + email);
        } catch (Exception e) {
            log.severe("Erro ao enviar email de recuperação para: " + email + ", erro: " + e.getMessage());
            throw new RuntimeException("Erro ao enviar email");
        }
    }
    
    private String buildPasswordResetEmailTemplate(String name, String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Redefinição de Senha</title>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #1976d2; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .button { display: inline-block; padding: 12px 24px; background-color: #1976d2; 
                             color: white; text-decoration: none; border-radius: 4px; margin: 20px 0; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🔐 VORTEX - Redefinição de Senha</h1>
                    </div>
                    <div class="content">
                        <h2>Olá, %s!</h2>
                        <p>Você solicitou a redefinição de sua senha no sistema VORTEX.</p>
                        <p>Clique no botão abaixo para redefinir sua senha:</p>
                        <a href="%s" class="button">Redefinir Senha</a>
                        <p>Se você não solicitou esta redefinição, ignore este email. Sua senha permanecerá inalterada.</p>
                        <p><strong>Este link expira em 30 minutos.</strong></p>
                    </div>
                    <div class="footer">
                        <p>Este é um email automático. Não responda a este email.</p>
                        <p>© 2024 VORTEX - Sistema de Controle de Estoque</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(name, resetLink);
    }
}