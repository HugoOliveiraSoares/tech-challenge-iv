package br.com.fiap.feedbackapi.infra.http;

import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.UUID;

public final class CorrelationIdProvider {
    private CorrelationIdProvider(){}

    public static String get(ContainerRequestContext requestContext){
        var correlationId = requestContext.getProperty(CorrelationIdFilter.CORRELATION_ID);

        if(correlationId == null || correlationId.toString().isBlank()){
            return UUID.randomUUID().toString();
        }

        return correlationId.toString();
    }
}
