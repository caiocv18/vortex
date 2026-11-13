package br.com.vortex.application.exception;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handler global para exceções da aplicação.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    /**
     * Manipula exceções de recurso não encontrado.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorDetails> handleResourceNotFoundException(
            ResourceNotFoundException exception, WebRequest webRequest) {
        
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                exception.getMessage(),
                webRequest.getDescription(false),
                "RESOURCE_NOT_FOUND");
        
        return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
    }

    /**
     * Manipula exceções de estoque insuficiente.
     */
    @ExceptionHandler(EstoqueInsuficienteException.class)
    public ResponseEntity<ErrorDetails> handleEstoqueInsuficienteException(
            EstoqueInsuficienteException exception, WebRequest webRequest) {
        
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                exception.getMessage(),
                webRequest.getDescription(false),
                "ESTOQUE_INSUFICIENTE");
        
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    /**
     * Manipula exceções de validação de argumentos.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception, WebRequest webRequest) {
        
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ValidationErrorDetails errorDetails = new ValidationErrorDetails(
                LocalDateTime.now(),
                "Erro de validação",
                webRequest.getDescription(false),
                "VALIDATION_ERROR",
                errors);
        
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    /**
     * Devolve ao Spring Security as recusas do {@code @PreAuthorize}. Sem este handler, o genérico abaixo
     * capturaria a {@code AuthorizationDeniedException} e responderia 500 a um papel sem permissão. Relançada
     * a mesma exceção, o Spring MVC desiste dela e o {@code ExceptionTranslationFilter} responde 403 (ou 401,
     * quando não há autenticação).
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityException(RuntimeException exception) {
        throw exception;
    }

    /**
     * Manipula exceções genéricas.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetails> handleGlobalException(
            Exception exception, WebRequest webRequest) {
        
        // Generate a unique error ID for tracking
        String errorId = UUID.randomUUID().toString();
        
        // Log the full exception details for debugging
        log.error("Unhandled exception [ID: {}] - URI: {}", 
                errorId, webRequest.getDescription(false), exception);
        
        // Determine if we're in production mode
        boolean isProduction = activeProfiles.contains("prd") || 
                              activeProfiles.contains("prod") || 
                              activeProfiles.contains("production");
        
        String userMessage;
        String technicalDetails = null;
        
        if (isProduction) {
            // In production, show generic message to avoid information disclosure
            userMessage = "Ocorreu um erro interno no servidor. ID do erro: " + errorId;
        } else {
            // In development, show more details
            userMessage = "Erro interno: " + exception.getMessage();
            technicalDetails = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                userMessage,
                webRequest.getDescription(false),
                "INTERNAL_SERVER_ERROR",
                errorId,
                technicalDetails);
        
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Classe para detalhes de erro.
     */
    @Getter
    public static class ErrorDetails {
        private final LocalDateTime timestamp;
        private final String message;
        private final String details;
        private final String errorCode;
        private final String errorId;
        private final String technicalDetails;

        public ErrorDetails(LocalDateTime timestamp, String message, String details, String errorCode) {
            this(timestamp, message, details, errorCode, null, null);
        }

        public ErrorDetails(LocalDateTime timestamp, String message, String details, String errorCode, 
                           String errorId, String technicalDetails) {
            this.timestamp = timestamp;
            this.message = message;
            this.details = details;
            this.errorCode = errorCode;
            this.errorId = errorId;
            this.technicalDetails = technicalDetails;
        }
    }

    /**
     * Classe para detalhes de erro de validação.
     */
    @Getter
    public static class ValidationErrorDetails extends ErrorDetails {
        private final Map<String, String> errors;

        public ValidationErrorDetails(LocalDateTime timestamp, String message, String details, 
                                     String errorCode, Map<String, String> errors) {
            super(timestamp, message, details, errorCode);
            this.errors = errors;
        }

    }
}