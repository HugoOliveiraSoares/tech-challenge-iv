package br.com.fiap.feedbackapi.infra.http.error;

import br.com.fiap.feedbackapi.infra.http.CorrelationIdProvider;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.List;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOGGER = Logger.getLogger(GenericExceptionMapper.class);

    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(Exception exception) {
        var correlationId = CorrelationIdProvider.get(requestContext);

        Throwable jacksonException = findJacksonException(exception);

        if(jacksonException instanceof JsonParseException){
            return JsonErrorResponseFactory.malformedJson(correlationId);
        }

        if(jacksonException instanceof InvalidFormatException invalidFormat){
            return JsonErrorResponseFactory.invalidFormat(invalidFormat, correlationId);
        }

        if(jacksonException instanceof JsonMappingException){
            return JsonErrorResponseFactory.invalidMapping(correlationId);
        }

        if (exception instanceof WebApplicationException webApplicationException) {
            return webApplicationException.getResponse();
        }

        LOGGER.errorf(exception,
                "Unexpected request error. correlationId=%s method=%s uri=%s",
                correlationId,
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri());

        var body = new ApiErrorResponse("INTERNAL_ERROR",
                "Erro interno ao processar a requisição",
                correlationId,
                List.of());

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(body)
                .build();
    }

    private Throwable findJacksonException(Exception exception) {
        Throwable current = exception;

        while(current != null){
            if(current instanceof JsonParseException ||
                    current instanceof InvalidFormatException ||
                    current instanceof JsonMappingException){
                return current;
            }

            current = current.getCause();
        }
        return  null;
    }
}
