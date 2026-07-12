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

@Provider
@UnwrapException({WebApplicationException.class})
public class JsonExceptionMappers {
    @Context
    ContainerRequestContext requestContext;

    @ServerExceptionMapper(JsonParseException.class)
    public Response mapJsonParseException(JsonParseException exception){
        var correlationId = CorrelationIdProvider.get(requestContext);

        return JsonErrorResponseFactory.malformedJson(correlationId);
    }

    @ServerExceptionMapper(InvalidFormatException.class)
    public Response mapInvalidFormatException(InvalidFormatException exception){
        var correlationId = CorrelationIdProvider.get(requestContext);

        return JsonErrorResponseFactory.invalidFormat(exception, correlationId);
    }

    @ServerExceptionMapper
    public Response mapJsonMappingException(JsonMappingException exception){
        var correlationId = CorrelationIdProvider.get(requestContext);

        return JsonErrorResponseFactory.invalidMapping(correlationId);
    }
}
