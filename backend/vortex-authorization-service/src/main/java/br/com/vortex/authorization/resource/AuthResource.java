package br.com.vortex.authorization.resource;

import br.com.vortex.authorization.dto.*;
import br.com.vortex.authorization.service.AuthService;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthResource.class);

    // Maior IP literal aceito: IPv6 com IPv4 embutido, colchetes e escopo cabem com folga
    static final int MAX_IP_LITERAL_LENGTH = 64;

    // Nos endpoints abaixo, corpo vazio chega como null e o @Valid sozinho não rejeita null:
    // sem o @NotNull a requisição caía em NullPointerException (500) em vez de responder 400.

    @Inject
    AuthService authService;

    @Context
    UriInfo uriInfo;
    
    @Context
    HttpHeaders httpHeaders;

    @Context
    HttpServerRequest httpServerRequest;

    @POST
    @Path("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT tokens")
    public Response login(@Valid @NotNull LoginRequest request) {
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();
        
        try {
            LoginResponse response = authService.login(request, ipAddress, userAgent);
            return Response.ok(ApiResponse.success("Login successful", response)).build();
        } catch (NotAuthorizedException | BadRequestException e) {
            // Credencial inválida ou rate limit: a mensagem é de negócio e vai para o cliente
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        } catch (RuntimeException e) {
            // Falha interna (banco, pool esgotado, chave do JWT ausente) não é credencial inválida.
            // Antes virava 401 com o texto da exceção ("Unable to acquire JDBC Connection ...").
            LOGGER.error("Unexpected error during login", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Login is temporarily unavailable. Please try again later."))
                .build();
        }
    }

    @POST
    @Path("/register")
    @Operation(summary = "User registration", description = "Register a new user account")
    public Response register(@Valid @NotNull RegisterRequest request) {
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();
        
        try {
            LoginResponse response = authService.register(request, ipAddress, userAgent);
            return Response.status(Response.Status.CREATED)
                .entity(ApiResponse.success("Registration successful", response))
                .build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/refresh")
    @Operation(summary = "Refresh access token", description = "Generate new access token using refresh token")
    public Response refreshToken(@Valid @NotNull RefreshTokenRequest request) {
        try {
            LoginResponse response = authService.refreshToken(request);
            return Response.ok(ApiResponse.success("Token refreshed successfully", response)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/logout")
    @Operation(summary = "User logout", description = "Invalidate refresh token and logout user")
    public Response logout(@Valid @NotNull RefreshTokenRequest request) {
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();
        
        authService.logout(request.refreshToken, ipAddress, userAgent);
        return Response.ok(ApiResponse.success("Logout successful")).build();
    }

    @POST
    @Path("/forgot-password")
    @Operation(summary = "Forgot password", description = "Request password reset email")
    public Response forgotPassword(@Valid @NotNull ForgotPasswordRequest request) {
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();
        
        try {
            authService.forgotPassword(request, ipAddress, userAgent);
            return Response.ok(ApiResponse.success("If the email exists, a password reset link has been sent")).build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/reset-password")
    @Operation(summary = "Reset password", description = "Reset password using reset token")
    public Response resetPassword(@Valid @NotNull ResetPasswordRequest request) {
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();
        
        try {
            authService.resetPassword(request, ipAddress, userAgent);
            return Response.ok(ApiResponse.success("Password reset successful")).build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/validate-token")
    @Operation(summary = "Validate JWT token", description = "Validate JWT token and return user information")
    public Response validateToken(@Valid @NotNull ValidateTokenRequest request) {
        try {
            ValidateTokenResponse response = authService.validateToken(request);
            return Response.ok(response).build();
        } catch (Exception e) {
            ValidateTokenResponse invalidResponse = new ValidateTokenResponse(false, null, null, null, null);
            return Response.ok(invalidResponse).build();
        }
    }

    private String getClientIpAddress() {
        // Só o endereço remoto da requisição. Com quarkus.http.proxy.* o Quarkus já o troca pelo
        // cliente do X-Forwarded-For quando a conexão vem de um proxy de TRUSTED_PROXIES (por padrão,
        // nenhum). Ler os cabeçalhos aqui deixava qualquer cliente escolher o próprio IP e fugir do
        // rate limit.
        String ip = ipLiteral(httpServerRequest.remoteAddress());
        if (ip == null && httpServerRequest.connection() != null) {
            // O Quarkus não valida o X-Forwarded-For: de um proxy confiável pode chegar um nome de host,
            // lixo ou um valor maior que ip_address (varchar 255), que derrubava o insert da falha e
            // anulava até o limite por e-mail. Nesse caso vale o par real da conexão (o proxy).
            ip = ipLiteral(httpServerRequest.connection().remoteAddress());
        }
        return ip != null ? ip : "unknown";
    }

    private static String ipLiteral(SocketAddress address) {
        if (address == null) {
            return null;
        }
        return normalizeIpLiteral(address.hostAddress() != null ? address.hostAddress() : address.host());
    }

    /**
     * Forma canônica (InetAddress.getHostAddress, sem escopo de IPv6) de um IP literal, ou null quando o
     * valor não é IP literal. Nunca resolve nome (InetAddress.ofLiteral não consulta DNS), e o IPv4
     * mapeado em IPv6 vira o IPv4: o mesmo cliente conta sempre com o mesmo texto.
     */
    static String normalizeIpLiteral(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_IP_LITERAL_LENGTH) {
            return null;
        }
        try {
            InetAddress address = InetAddress.ofLiteral(value);
            return InetAddress.getByAddress(address.getAddress()).getHostAddress();
        } catch (IllegalArgumentException | UnknownHostException e) {
            return null;
        }
    }

    private String getUserAgent() {
        return httpHeaders.getRequestHeaders().getFirst("User-Agent");
    }
}