package br.com.fiap.feedbackplatform.shared.domain;

import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import java.util.UUID;

public record CriticalFeedbackEvent(UUID feedbackId, String correlationId) {
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

        return new CriticalFeedbackEvent(feedback.id(), feedback.correlationId());
    }
}
