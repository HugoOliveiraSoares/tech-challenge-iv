package br.com.fiap.feedbackplatform.shared.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeedbackTest {
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant DATA_ENVIO = Instant.parse("2026-01-01T10:00:00Z");
    private static final String DESCRICAO_VALIDA = "A aula estava confusa e nao consegui acompanhar.";

    @Test
    void criaFeedbackComUrgenciaPeriodoECorrelationId() {
        Feedback feedback = Feedback.criar(ID, DESCRICAO_VALIDA, 2, DATA_ENVIO, " correlation-1 ");

        assertEquals(ID, feedback.id());
        assertEquals(DESCRICAO_VALIDA, feedback.descricao());
        assertEquals(2, feedback.nota());
        assertEquals(Urgencia.CRITICA, feedback.urgencia());
        assertEquals(DATA_ENVIO, feedback.dataEnvio());
        assertEquals("2026-W01", feedback.periodo());
        assertEquals("correlation-1", feedback.correlationId());
    }

    @Test
    void normalizaDescricaoECorrelationIdEmBranco() {
        Feedback feedback = Feedback.criar(ID, "  Descricao valida  ", 8, DATA_ENVIO, "   ");

        assertEquals("Descricao valida", feedback.descricao());
        assertNull(feedback.correlationId());
    }

    @Test
    void rejeitaDescricaoAusente() {
        assertThrows(DomainValidationException.class, () -> Feedback.criar(ID, null, 8, DATA_ENVIO, null));
        assertThrows(DomainValidationException.class, () -> Feedback.criar(ID, "   ", 8, DATA_ENVIO, null));
    }

    @Test
    void rejeitaDescricaoCurta() {
        assertThrows(DomainValidationException.class, () -> Feedback.criar(ID, "curta", 8, DATA_ENVIO, null));
    }

    @Test
    void rejeitaDescricaoLonga() {
        String descricaoLonga = "a".repeat(1001);

        assertThrows(DomainValidationException.class, () -> Feedback.criar(ID, descricaoLonga, 8, DATA_ENVIO, null));
    }

    @Test
    void rejeitaIdAusente() {
        assertThrows(DomainValidationException.class, () -> Feedback.criar(null, DESCRICAO_VALIDA, 8, DATA_ENVIO, null));
    }

    @Test
    void rejeitaUrgenciaIncompativelComNota() {
        assertThrows(DomainValidationException.class, () -> new Feedback(
                ID,
                DESCRICAO_VALIDA,
                2,
                Urgencia.BAIXA,
                DATA_ENVIO,
                "2026-W01",
                null));
    }

    @Test
    void rejeitaPeriodoIncompativelComDataEnvio() {
        assertThrows(DomainValidationException.class, () -> new Feedback(
                ID,
                DESCRICAO_VALIDA,
                8,
                Urgencia.BAIXA,
                DATA_ENVIO,
                "2026-W02",
                null));
    }
}
