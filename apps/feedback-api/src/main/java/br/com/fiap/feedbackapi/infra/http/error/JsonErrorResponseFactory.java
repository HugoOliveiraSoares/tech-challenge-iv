package br.com.fiap.feedbackapi.infra.http.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.ws.rs.core.Response;

import java.util.List;

public final class JsonErrorResponseFactory {
    private JsonErrorResponseFactory(){}

    public static Response malformedJson(String correlationId){
        var body = new ApiErrorResponse("MALFORMED_JSON",
                "Corpo da requisição não é um JSON válido",
                correlationId,
                List.of());

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(body)
                .build();
    }

    public static Response invalidFormat(InvalidFormatException exception,
                                         String correlationId){
        var fieldName = exception.getPath().isEmpty() ? null : exception.getPath().getLast().getFieldName();

        var body = new ApiErrorResponse("VALIDATION_ERROR",
                "Campo com tipo inválido no corpo da requisição",
                correlationId,
                List.of(new ApiErrorDetail(fieldName, "tipo inválido")));

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(body)
                .build();
    }

    public static Response invalidMapping(String correlationId){
        var body = new ApiErrorResponse("VALIDATION_ERROR",
                "Campo com tipo inválido no corpo da requisição",
                correlationId,
                List.of());

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(body)
                .build();
    }
}
