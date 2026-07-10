package br.com.fiap.feedbackplatform.shared.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CriticalFeedbackEventTest {
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void criaEventoAPartirDeFeedback() {
        Feedback feedback = Feedback.criar(
                ID,
                "A aula estava confusa e nao consegui acompanhar.",
                2,
                Instant.parse("2026-01-01T10:00:00Z"),
                "correlation-1");

        CriticalFeedbackEvent event = CriticalFeedbackEvent.from(feedback);

        assertEquals(ID, event.feedbackId());
        assertEquals("correlation-1", event.correlationId());
    }

    @Test
    void normalizaCorrelationIdEmBranco() {
        CriticalFeedbackEvent event = new CriticalFeedbackEvent(ID, "   ");

        assertNull(event.correlationId());
    }

    @Test
    void rejeitaFeedbackIdAusente() {
        assertThrows(DomainValidationException.class, () -> new CriticalFeedbackEvent(null, null));
    }
}
