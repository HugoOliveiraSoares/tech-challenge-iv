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
    private static final Instant DATA_ENVIO = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void criaEventoAPartirDeFeedback() {
        Feedback feedback = Feedback.criar(
                ID,
                "A aula estava confusa e nao consegui acompanhar.",
                2,
                DATA_ENVIO,
                "correlation-1");

        CriticalFeedbackEvent event = CriticalFeedbackEvent.from(feedback);

        assertEquals(ID, event.feedbackId());
        assertEquals("correlation-1", event.correlationId());
        assertEquals("A aula estava confusa e nao consegui acompanhar.", event.descricao());
        assertEquals(2, event.nota());
        assertEquals(Urgencia.CRITICA, event.urgencia());
        assertEquals(DATA_ENVIO, event.dataEnvio());
    }

    @Test
    void normalizaCorrelationIdEmBranco() {
        CriticalFeedbackEvent event = new CriticalFeedbackEvent(
                ID,
                "   ",
                "Descricao valida para teste.",
                2,
                Urgencia.CRITICA,
                DATA_ENVIO);

        assertNull(event.correlationId());
    }

    @Test
    void rejeitaFeedbackIdAusente() {
        assertThrows(
                DomainValidationException.class,
                () -> new CriticalFeedbackEvent(null, null, "Descricao valida.", 2, Urgencia.CRITICA, DATA_ENVIO));
    }

    @Test
    void rejeitaDescricaoAusente() {
        assertThrows(
                DomainValidationException.class,
                () -> new CriticalFeedbackEvent(ID, null, "   ", 2, Urgencia.CRITICA, DATA_ENVIO));
    }

    @Test
    void rejeitaNotaInvalida() {
        assertThrows(
                DomainValidationException.class,
                () -> new CriticalFeedbackEvent(ID, null, "Descricao valida.", 11, Urgencia.CRITICA, DATA_ENVIO));
    }
}
