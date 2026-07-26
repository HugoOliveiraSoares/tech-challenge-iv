package br.com.fiap.feedbackapi.infra.http.error;

import br.com.fiap.feedbackapi.core.exception.PersistenceException;
import br.com.fiap.feedbackapi.infra.http.CorrelationIdProvider;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class PersistenceExceptionMapper implements ExceptionMapper<PersistenceException> {
    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(PersistenceException exception) {
        var correlationId = CorrelationIdProvider.get(requestContext);

        var body = new ApiErrorResponse("PERSISTENCE_ERROR",
                "Não foi possivel persistir o feedback.",
                correlationId,
                List.of());

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(body)
                .build();
    }
}
