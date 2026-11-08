package br.com.vortex.authorization.service;

import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EmailService {

    @Inject
    ReactiveMailer mailer;

    @ConfigProperty(name = "quarkus.mailer.from")
    String fromEmail;

    @ConfigProperty(name = "auth.frontend.url", defaultValue = "http://localhost:3001")
    String authFrontendUrl;

    public Uni<Void> sendPasswordResetEmail(String toEmail, String resetToken) {
        try {
            String resetUrl = authFrontendUrl + "/reset-password?token=" + resetToken;
            
            String subject = "Vortex - Redefinição de Senha";
            String htmlBody = buildPasswordResetEmailHtml(resetUrl);
            String textBody = buildPasswordResetEmailText(resetUrl);

            Mail mail = Mail.withHtml(toEmail, subject, htmlBody)
                          .setText(textBody);

            Log.infof("Sending password reset email to: %s", toEmail);
            
            return mailer.send(mail)
                    .invoke(() -> Log.infof("Password reset email sent successfully to: %s", toEmail))
                    .onFailure()
                    .invoke(throwable -> Log.errorf(throwable, "Failed to send password reset email to: %s", toEmail));
                    
        } catch (Exception e) {
            Log.errorf(e, "Error preparing password reset email for: %s", toEmail);
            return Uni.createFrom().failure(e);
        }
    }

    public Uni<Void> sendWelcomeEmail(String toEmail, String username) {
        try {
            String subject = "Bem-vindo ao Vortex!";
            String htmlBody = buildWelcomeEmailHtml(username);
            String textBody = buildWelcomeEmailText(username);

            Mail mail = Mail.withHtml(toEmail, subject, htmlBody)
                          .setText(textBody);

            Log.infof("Sending welcome email to: %s", toEmail);
            
            return mailer.send(mail)
                    .invoke(() -> Log.infof("Welcome email sent successfully to: %s", toEmail))
                    .onFailure()
                    .invoke(throwable -> Log.errorf(throwable, "Failed to send welcome email to: %s", toEmail));
                    
        } catch (Exception e) {
            Log.errorf(e, "Error preparing welcome email for: %s", toEmail);
            return Uni.createFrom().failure(e);
        }
    }

    private String buildPasswordResetEmailHtml(String resetUrl) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c3e50; text-align: center;">Redefinição de Senha - Vortex</h2>
                    
                    <p>Olá,</p>
                    
                    <p>Você solicitou a redefinição da sua senha na plataforma Vortex.</p>
                    
                    <p>Para redefinir sua senha, clique no botão abaixo:</p>
                    
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" 
                           style="background-color: #3498db; color: white; padding: 12px 25px; 
                                  text-decoration: none; border-radius: 5px; display: inline-block;">
                            Redefinir Senha
                        </a>
                    </div>
                    
                    <p>Ou copie e cole o link abaixo em seu navegador:</p>
                    <p style="word-break: break-all; color: #7f8c8d;">%s</p>
                    
                    <p><strong>Este link expira em 1 hora por motivos de segurança.</strong></p>
                    
                    <p>Se você não solicitou a redefinição de senha, ignore este email.</p>
                    
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    
                    <p style="font-size: 12px; color: #7f8c8d; text-align: center;">
                        Esta mensagem foi enviada automaticamente. Não responda a este email.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(resetUrl, resetUrl);
    }

    private String buildPasswordResetEmailText(String resetUrl) {
        return """
            Redefinição de Senha - Vortex
            
            Olá,
            
            Você solicitou a redefinição da sua senha na plataforma Vortex.
            
            Para redefinir sua senha, acesse o link abaixo:
            %s
            
            Este link expira em 1 hora por motivos de segurança.
            
            Se você não solicitou a redefinição de senha, ignore este email.
            
            ---
            Esta mensagem foi enviada automaticamente. Não responda a este email.
            """.formatted(resetUrl);
    }

    private String buildWelcomeEmailHtml(String username) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c3e50; text-align: center;">Bem-vindo ao Vortex!</h2>
                    
                    <p>Olá %s,</p>
                    
                    <p>Seja bem-vindo à plataforma Vortex! Sua conta foi criada com sucesso.</p>
                    
                    <p>Agora você pode acessar todos os recursos disponíveis em nossa plataforma de controle de estoque.</p>
                    
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" 
                           style="background-color: #27ae60; color: white; padding: 12px 25px; 
                                  text-decoration: none; border-radius: 5px; display: inline-block;">
                            Acessar Plataforma
                        </a>
                    </div>
                    
                    <p>Se você tiver alguma dúvida ou precisar de ajuda, entre em contato com nosso suporte.</p>
                    
                    <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;">
                    
                    <p style="font-size: 12px; color: #7f8c8d; text-align: center;">
                        Esta mensagem foi enviada automaticamente. Não responda a este email.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(username, authFrontendUrl);
    }

    private String buildWelcomeEmailText(String username) {
        return """
            Bem-vindo ao Vortex!
            
            Olá %s,
            
            Seja bem-vindo à plataforma Vortex! Sua conta foi criada com sucesso.
            
            Agora você pode acessar todos os recursos disponíveis em nossa plataforma de controle de estoque.
            
            Acesse: %s
            
            Se você tiver alguma dúvida ou precisar de ajuda, entre em contato com nosso suporte.
            
            ---
            Esta mensagem foi enviada automaticamente. Não responda a este email.
            """.formatted(username, authFrontendUrl);
    }
}
