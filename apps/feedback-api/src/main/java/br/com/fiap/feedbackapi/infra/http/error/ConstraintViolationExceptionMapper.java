package br.com.fiap.feedbackapi.infra.http.error;

import br.com.fiap.feedbackapi.infra.http.CorrelationIdProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.util.List;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        var correlationId = CorrelationIdProvider.get(requestContext);

        List<ApiErrorDetail> details = exception.getConstraintViolations().stream()
                .map(violation -> new ApiErrorDetail(
                        extractFieldName(violation),
                        violation.getMessage()
                ))
                .toList();

        boolean hasRequiredFieldError = exception.getConstraintViolations().stream()
                .anyMatch(this::isRequiredFieldViolation);

        var status = hasRequiredFieldError ? Response.Status.BAD_REQUEST.getStatusCode() : 422;
        var code = hasRequiredFieldError ? "VALIDATION_ERROR" : "BUSINESS_RULE_ERROR";
        var message = hasRequiredFieldError ? "Campos obrigatórios ausentes ou inválidos" : "Regra de negocio violada";

        var body = new ApiErrorResponse(code, message, correlationId, details);

        return Response.status(status)
                .entity(body)
                .build();
    }

    private boolean isRequiredFieldViolation(ConstraintViolation<?> violation) {
        Annotation annotation = violation.getConstraintDescriptor().getAnnotation();

        return annotation instanceof NotNull || annotation instanceof NotBlank;
    }

    private String extractFieldName(ConstraintViolation<?> violation) {
        var path = violation.getPropertyPath().toString();

        if (path == null || path.isBlank()){
            return null;
        }

        var lastDotIndex = path.lastIndexOf(".");

        if(lastDotIndex >= 0 && lastDotIndex < path.length()-1){
            return path.substring(lastDotIndex+1);
        }

        return path;
    }
}
