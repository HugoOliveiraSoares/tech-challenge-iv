package br.com.fiap.feedbackapi.infra.http.error;

import br.com.fiap.feedbackapi.infra.http.CorrelationIdProvider;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {
    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(Exception exception) {
        var correlationId = CorrelationIdProvider.get(requestContext);

        var body = new ApiErrorResponse("INTERNAL_ERROR",
                "Erro interno ao processar a requisicao",
                correlationId,
                List.of());

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(body)
                .build();
    }
}
