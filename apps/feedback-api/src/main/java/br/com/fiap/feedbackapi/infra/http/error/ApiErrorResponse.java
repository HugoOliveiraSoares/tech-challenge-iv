package br.com.fiap.feedbackapi.infra.http.error;

import java.util.List;

public record ApiErrorResponse(String code,
                               String message,
                               String correlationId,
                               List<ApiErrorDetail> details) {
}
