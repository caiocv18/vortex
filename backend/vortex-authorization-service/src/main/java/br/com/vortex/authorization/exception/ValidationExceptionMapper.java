package br.com.vortex.authorization.exception;

import br.com.vortex.authorization.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        StringBuilder message = new StringBuilder("Validation failed: ");
        
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            message.append(violation.getPropertyPath())
                   .append(" ")
                   .append(violation.getMessage())
                   .append("; ");
        }
        
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(message.toString()))
                .build();
    }
}