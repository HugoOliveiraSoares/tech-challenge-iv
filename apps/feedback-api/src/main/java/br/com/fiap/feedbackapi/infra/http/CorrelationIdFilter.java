package br.com.fiap.feedbackapi.infra.http;

import br.com.fiap.feedbackapi.infra.http.error.ApiErrorDetail;
import br.com.fiap.feedbackapi.infra.http.error.ApiErrorResponse;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String CORRELATION_ID = "correlationId";

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 100;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        var correlationId = requestContext.getHeaderString(HttpHeadersName.X_CORRELATION_ID);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        requestContext.setProperty(CORRELATION_ID, correlationId);

        if (!isValid(correlationId)) {
            var body = new ApiErrorResponse("VALIDATION_ERROR",
                    "X-Correlation-Id deve conter entre 8 e 100 caracteres",
                    correlationId,
                    List.of(new ApiErrorDetail(HttpHeadersName.X_CORRELATION_ID,
                            "tamanho permitido entre 8 e 100 caracteres")
                    ));

            requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST)
                    .entity(body)
                    .build());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        var correlationId = requestContext.getProperty(CORRELATION_ID);

        if(correlationId != null){
            responseContext.getHeaders().putSingle(HttpHeadersName.X_CORRELATION_ID, correlationId.toString());
        }
    }

    private boolean isValid(String correlationId) {
        var length = correlationId.length();

        return length >= MIN_LENGTH && length <= MAX_LENGTH;
    }
}
