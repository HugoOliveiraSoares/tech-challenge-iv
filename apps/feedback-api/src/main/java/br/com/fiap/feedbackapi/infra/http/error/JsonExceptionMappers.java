package br.com.fiap.feedbackapi.infra.http.error;

import br.com.fiap.feedbackapi.infra.http.CorrelationIdProvider;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.jboss.resteasy.reactive.server.UnwrapException;

import java.util.List;

@Provider
@UnwrapException({WebApplicationException.class, RuntimeException.class})
public class JsonExceptionMappers {
    @Context
    ContainerRequestContext requestContext;

    @ServerExceptionMapper(JsonParseException.class)
    public Response mapJsonParseException(JsonParseException exception){
        var correlationId = CorrelationIdProvider.get(requestContext);

        var body = new ApiErrorResponse(
                "MALFORMED_JSON",
                "Corpo da requisição nao é um JSON válido",
                correlationId,
                List.of());

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(body)
                .build();
    }

    @ServerExceptionMapper(InvalidFormatException.class)
    public Response mapInvalidFormatException(InvalidFormatException exception){
        var correlationId = CorrelationIdProvider.get(requestContext);

        var fieldName = exception.getPath().isEmpty() ? null : exception.getPath().getLast().getFieldName();
        var body = new ApiErrorResponse("VALIDATION_ERROR",
                "Campo com tipo inválido no corpo da requisição",
                correlationId,
                List.of(new ApiErrorDetail(fieldName, "tipo inválido")));

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(body)
                .build();
    }

    @ServerExceptionMapper
    public Response mapJsonMappingException(JsonMappingException exception){
        var correlationId = CorrelationIdProvider.get(requestContext);
        var body = new ApiErrorResponse("VALIDATION_ERROR",
                "Campo com tipo inválido no corpo da requisição",
                correlationId,
                List.of());

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(body)
                .build();
    }

}
