package br.com.fiap.feedbackapi.infra.http;

import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.UUID;

public final class CorrelationIdProvider {
    private CorrelationIdProvider(){}

    public static String get(ContainerRequestContext requestContext){
        var property = requestContext.getProperty(CorrelationIdFilter.CORRELATION_ID);

        if(property != null && !property.toString().isBlank()){
            return property.toString();
        }

        var correlationId = UUID.randomUUID().toString();

        requestContext.setProperty(CorrelationIdFilter.CORRELATION_ID, correlationId);

        return correlationId;
    }
}
