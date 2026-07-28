package br.com.fiap.feedbackplatform.shared.domain;

import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;

import java.time.Instant;
import java.util.UUID;

public record CriticalFeedbackEvent(String eventType,
                                    String eventVersion,
                                    UUID feedbackId,
                                    String descricao,
                                    int nota,
                                    Urgencia urgencia,
                                    Instant dataEnvio,
                                    String periodo,
                                    String correlationId) {
    public CriticalFeedbackEvent {
        if (feedbackId == null) {
            throw new DomainValidationException("Feedback id e obrigatorio.");
        }

        if (correlationId != null && correlationId.isBlank()) {
            correlationId = null;
        } else if (correlationId != null) {
            correlationId = correlationId.trim();
        }
    }

    public static CriticalFeedbackEvent from(Feedback feedback) {
        if (feedback == null) {
            throw new DomainValidationException("Feedback e obrigatorio.");
        }

        return new CriticalFeedbackEvent("FeedbackCritico",
                "1.0",
                feedback.id(),
                feedback.descricao(),
                feedback.nota(),
                feedback.urgencia(),
                feedback.dataEnvio(),
                feedback.periodo(),
                feedback.correlationId());
    }
}
