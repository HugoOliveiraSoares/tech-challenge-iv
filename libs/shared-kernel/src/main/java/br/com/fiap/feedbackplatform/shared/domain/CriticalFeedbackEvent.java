package br.com.fiap.feedbackplatform.shared.domain;

import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import java.time.Instant;
import java.util.UUID;

public record CriticalFeedbackEvent(
        UUID feedbackId,
        String correlationId,
        String descricao,
        int nota,
        Urgencia urgencia,
        Instant dataEnvio) {

    public CriticalFeedbackEvent {
        if (feedbackId == null) {
            throw new DomainValidationException("Feedback id e obrigatorio.");
        }

        if (descricao == null || descricao.isBlank()) {
            throw new DomainValidationException("Descricao e obrigatoria.");
        }
        descricao = descricao.trim();

        if (nota < 0 || nota > 10) {
            throw new DomainValidationException("Nota deve estar entre 0 e 10.");
        }

        if (urgencia == null) {
            throw new DomainValidationException("Urgencia e obrigatoria.");
        }

        if (dataEnvio == null) {
            throw new DomainValidationException("Data de envio e obrigatoria.");
        }

        correlationId = normalizarCorrelationId(correlationId);
    }

    public static CriticalFeedbackEvent from(Feedback feedback) {
        if (feedback == null) {
            throw new DomainValidationException("Feedback e obrigatorio.");
        }

        return new CriticalFeedbackEvent(
                feedback.id(),
                feedback.correlationId(),
                feedback.descricao(),
                feedback.nota(),
                feedback.urgencia(),
                feedback.dataEnvio());
    }

    private static String normalizarCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return null;
        }

        return correlationId.trim();
    }
}
