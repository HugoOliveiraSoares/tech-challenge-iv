package br.com.fiap.feedbackapi.infra.http.error;

import br.com.fiap.feedbackapi.core.exception.NotificationException;
import br.com.fiap.feedbackapi.infra.http.CorrelationIdProvider;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class NotificationExceptionMapper implements ExceptionMapper<NotificationException> {
    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(NotificationException exception) {
        var correlationId = CorrelationIdProvider.get(requestContext);

        var body = new ApiErrorResponse("NOTIFICATION_ERROR",
                "Não foi possivel publicar a notificação critica.",
                correlationId,
                List.of());

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(body)
                .build();
    }
}
