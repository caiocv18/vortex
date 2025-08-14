package br.com.vortex.authorization.exception;

import br.com.vortex.authorization.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    @Override
    public Response toResponse(JsonProcessingException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error("Invalid JSON format"))
                .build();
    }
}