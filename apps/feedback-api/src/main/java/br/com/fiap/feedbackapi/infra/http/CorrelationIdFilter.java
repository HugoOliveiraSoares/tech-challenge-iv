package br.com.fiap.feedbackapi.infra.http;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.UUID;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String CORRELATION_ID = "correlationId";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        var correlationId = requestContext.getHeaderString(HttpHeadersName.X_CORRELATION_ID);

        if(correlationId == null || correlationId.isBlank()){
            correlationId = UUID.randomUUID().toString();
        }

        requestContext.setProperty(CORRELATION_ID, correlationId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        var correlationId = requestContext.getProperty(CORRELATION_ID);

        if(correlationId != null){
            responseContext.getHeaders().putSingle(HttpHeadersName.X_CORRELATION_ID, correlationId.toString());
        }
    }
}
