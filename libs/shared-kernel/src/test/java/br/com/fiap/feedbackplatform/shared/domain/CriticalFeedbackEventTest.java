package br.com.fiap.feedbackplatform.shared.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CriticalFeedbackEventTest {
    private static final String EVENT_TYPE = "FeedbackCritico";
    private static final String VERSION = "1.0";
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String DESCRICAO = "A aula estava confusa e nao consegui acompanhar";
    private static final int NOTA = 2;
    private static final Urgencia URG = Urgencia.CRITICA;
    private static final Instant DATA_ENVIO = Instant.parse("2026-01-01T10:00:00Z");
    private static final String PERIODO = "2026-W01";
    private static final String CORRELATION_ID = "correlation-1";

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
        CriticalFeedbackEvent event = new CriticalFeedbackEvent(EVENT_TYPE,
                VERSION,
                ID,
                DESCRICAO,
                NOTA,
                URG,
                DATA_ENVIO,
                PERIODO,
                "   ");

        assertNull(event.correlationId());
    }

    @Test
    void preservaCamposENormalizaTextos() {
        CriticalFeedbackEvent event = new CriticalFeedbackEvent(
                ID,
                " correlation-1 ",
                " Descricao valida para teste. ",
                3,
                Urgencia.CRITICA,
                DATA_ENVIO);

        assertEquals(ID, event.feedbackId());
        assertEquals("correlation-1", event.correlationId());
        assertEquals("Descricao valida para teste.", event.descricao());
        assertEquals(3, event.nota());
        assertEquals(Urgencia.CRITICA, event.urgencia());
        assertEquals(DATA_ENVIO, event.dataEnvio());
    }

    @Test
    void rejeitaFeedbackIdAusente() {
        assertThrows(DomainValidationException.class, () -> new CriticalFeedbackEvent(EVENT_TYPE,
                VERSION,
                null,
                DESCRICAO,
                NOTA,
                URG,
                DATA_ENVIO,
                PERIODO,
                CORRELATION_ID));
    }

    @Test
    void rejeitaUrgenciaAusente() {
        assertThrows(
                DomainValidationException.class,
                () -> new CriticalFeedbackEvent(ID, null, "Descricao valida.", 2, null, DATA_ENVIO));
    }

    @Test
    void rejeitaDataEnvioAusente() {
        assertThrows(
                DomainValidationException.class,
                () -> new CriticalFeedbackEvent(ID, null, "Descricao valida.", 2, Urgencia.CRITICA, null));
    }

    @Test
    void rejeitaFeedbackAusente() {
        assertThrows(DomainValidationException.class, () -> CriticalFeedbackEvent.from(null));
    }
}
