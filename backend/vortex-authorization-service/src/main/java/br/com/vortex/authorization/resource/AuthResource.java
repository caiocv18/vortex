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

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthResource {

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
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ApiResponse.error(e.getMessage()))
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
        // Try to get real IP from various headers
        String xForwardedFor = httpHeaders.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = httpHeaders.getRequestHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // Fallback to the socket remote address (may be a proxy). Antes caía no literal "unknown":
        // todo cliente sem cabeçalho de proxy dividia o mesmo balde do rate limit.
        SocketAddress remoteAddress = httpServerRequest.remoteAddress();
        if (remoteAddress != null) {
            String host = remoteAddress.hostAddress() != null ? remoteAddress.hostAddress() : remoteAddress.host();
            if (host != null && !host.isEmpty()) {
                return host;
            }
        }
        return "unknown";
    }

    private String getUserAgent() {
        return httpHeaders.getRequestHeaders().getFirst("User-Agent");
    }
}