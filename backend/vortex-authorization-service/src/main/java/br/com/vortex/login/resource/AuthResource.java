package br.com.vortex.login.resource;

import br.com.vortex.login.service.AuthService;
import br.com.vortex.login.service.AuditService;
import br.com.vortex.login.dto.*;
import br.com.vortex.login.exception.AuthenticationException;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;
import java.util.logging.Logger;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "Endpoints de autenticação")
public class AuthResource {
    
    private static final Logger log = Logger.getLogger(AuthResource.class.getName());
    
    @Inject
    AuthService authService;
    
    @Inject
    AuditService auditService;
    
    // ===== AUTENTICAÇÃO LOCAL =====
    
    @POST
    @Path("/login")
    @Operation(summary = "Login com email e senha", description = "Autentica usuário com credenciais locais")
    public Response login(@Valid LoginRequestDTO loginRequest) {
        try {
            log.info("Tentativa de login local para email: " + loginRequest.getEmail());
            LoginResponseDTO response = authService.authenticateLocal(loginRequest);
            
            auditService.publishLoginEvent(response.getUser(), "LOCAL_LOGIN_SUCCESS", "SUCCESS");
            
            return Response.ok(response).build();
        } catch (AuthenticationException e) {
            log.severe("Erro de autenticação local para email: " + loginRequest.getEmail() + ", erro: " + e.getMessage());
            auditService.publishLoginEvent(null, "LOCAL_LOGIN_FAILED", "FAILED");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Credenciais inválidas", "message", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.severe("Erro interno no login local: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro interno do servidor"))
                    .build();
        }
    }
    
    @POST
    @Path("/register")
    @Operation(summary = "Cadastro de novo usuário", description = "Cria nova conta de usuário")
    public Response register(@Valid RegisterRequestDTO registerRequest) {
        try {
            log.info("Tentativa de cadastro para email: " + registerRequest.getEmail());
            LoginResponseDTO response = authService.registerUser(registerRequest);
            
            auditService.publishUserEvent(response.getUser(), "USER_CREATED", "SUCCESS");
            
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (IllegalArgumentException e) {
            log.severe("Erro de validação no cadastro: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Dados inválidos", "message", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.severe("Erro interno no cadastro: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro interno do servidor"))
                    .build();
        }
    }
    
    @POST
    @Path("/forgot-password")
    @Operation(summary = "Solicitar recuperação de senha", description = "Envia email para recuperação de senha")
    public Response forgotPassword(@Valid ForgotPasswordRequestDTO forgotPasswordRequest) {
        try {
            log.info("Solicitação de recuperação de senha para email: " + forgotPasswordRequest.getEmail());
            authService.requestPasswordReset(forgotPasswordRequest.getEmail());
            
            auditService.publishPasswordResetEvent(forgotPasswordRequest.getEmail(), "PASSWORD_RESET_REQUESTED", "SUCCESS");
            
            return Response.ok(Map.of("message", "Se o email existir, você receberá instruções para redefinir sua senha")).build();
        } catch (Exception e) {
            log.severe("Erro ao solicitar recuperação de senha: " + e.getMessage());
            // Sempre retorna sucesso por segurança (não revelar se email existe)
            return Response.ok(Map.of("message", "Se o email existir, você receberá instruções para redefinir sua senha")).build();
        }
    }
    
    @POST
    @Path("/reset-password")
    @Operation(summary = "Redefinir senha", description = "Redefine senha usando token de recuperação")
    public Response resetPassword(@Valid ResetPasswordRequestDTO resetPasswordRequest) {
        try {
            log.info("Tentativa de redefinição de senha com token");
            authService.resetPassword(resetPasswordRequest);
            
            auditService.publishPasswordResetEvent(null, "PASSWORD_RESET_COMPLETED", "SUCCESS");
            
            return Response.ok(Map.of("message", "Senha redefinida com sucesso")).build();
        } catch (IllegalArgumentException e) {
            log.severe("Erro na redefinição de senha: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Token inválido ou expirado", "message", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.severe("Erro interno na redefinição de senha: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro interno do servidor"))
                    .build();
        }
    }
    
    // ===== AUTENTICAÇÃO OAUTH =====
    
    @GET
    @Path("/login/{provider}")
    @Operation(summary = "Redireciona para provedor OIDC", description = "Inicia o fluxo de autenticação OAuth2")
    public Response redirectToProvider(@PathParam("provider") String provider) {
        try {
            log.info("Iniciando autenticação para provider: " + provider);
            String redirectUrl = authService.getAuthorizationUrl(provider);
            return Response.temporaryRedirect(java.net.URI.create(redirectUrl)).build();
        } catch (Exception e) {
            log.severe("Erro ao redirecionar para provider: " + provider + ", erro: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Provider não suportado: " + provider))
                    .build();
        }
    }
    
    @GET
    @Path("/callback/{provider}")
    @Operation(summary = "Processa callback do provedor", description = "Processa o retorno da autenticação e gera JWT")
    public Response handleCallback(@PathParam("provider") String provider,
                                 @QueryParam("code") String code,
                                 @QueryParam("state") String state) {
        try {
            log.info("Processando callback para provider: " + provider);
            LoginResponseDTO response = authService.processCallback(provider, code, state);
            
            auditService.publishLoginEvent(response.getUser(), "OAUTH_LOGIN_SUCCESS", "SUCCESS");
            
            return Response.ok(response).build();
        } catch (AuthenticationException e) {
            log.severe("Erro de autenticação para provider: " + provider + ", erro: " + e.getMessage());
            auditService.publishLoginEvent(null, "OAUTH_LOGIN_FAILED", "FAILED");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Falha na autenticação", "message", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.severe("Erro interno no callback para provider: " + provider + ", erro: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro interno do servidor"))
                    .build();
        }
    }
    
    // ===== ENDPOINTS JWKS =====
    
    @GET
    @Path("/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retorna chaves públicas JWT", description = "Endpoint para validação de tokens JWT")
    public Response getJWKS() {
        try {
            Object jwks = authService.getJWKS();
            return Response.ok(jwks).build();
        } catch (Exception e) {
            log.severe("Erro ao obter JWKS: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro ao obter chaves públicas"))
                    .build();
        }
    }
    
    @GET
    @Path("/.well-known/openid-configuration")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Configuração OpenID Connect", description = "Endpoint de descoberta OpenID Connect")
    public Response getOpenIdConfiguration() {
        try {
            Object config = authService.getOpenIdConfiguration();
            return Response.ok(config).build();
        } catch (Exception e) {
            log.severe("Erro ao obter configuração OpenID: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Erro ao obter configuração"))
                    .build();
        }
    }
    
    @GET
    @Path("/health")
    @Operation(summary = "Health check", description = "Verifica saúde do serviço de autenticação")
    public Response healthCheck() {
        return Response.ok(Map.of(
            "status", "UP",
            "service", "vortex-auth-service",
            "timestamp", java.time.LocalDateTime.now(),
            "version", "1.0.0"
        )).build();
    }
}