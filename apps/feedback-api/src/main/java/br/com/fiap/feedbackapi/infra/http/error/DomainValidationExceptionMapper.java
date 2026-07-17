package br.com.fiap.feedbackapi.infra.http.error;

import br.com.fiap.feedbackapi.infra.http.CorrelationIdProvider;
import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class DomainValidationExceptionMapper implements ExceptionMapper<DomainValidationException> {
    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(DomainValidationException exception) {
        var correlationId = CorrelationIdProvider.get(requestContext);

        var body = new ApiErrorResponse("BUSINESS_RULE_ERROR",
                exception.getMessage(),
                correlationId,
                List.of());

        return Response.status(422)
                .entity(body)
                .build();
    }
}
