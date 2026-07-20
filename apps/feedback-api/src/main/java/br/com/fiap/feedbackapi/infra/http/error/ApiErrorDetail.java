package br.com.fiap.feedbackapi.infra.http.error;

public record ApiErrorDetail(String field,
                             String message) {
}
