package br.com.fiap.feedbackplatform.shared.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PeriodoIsoWeekTest {
    @Test
    void calculaPeriodoIsoWeekComFormatoAnoSemana() {
        assertEquals("2026-W01", PeriodoIsoWeek.from(Instant.parse("2026-01-01T10:00:00Z")));
    }

    @Test
    void calculaPeriodoIsoWeekEmViradaDeAno() {
        assertEquals("2020-W01", PeriodoIsoWeek.from(Instant.parse("2019-12-30T10:00:00Z")));
    }

    @Test
    void permaneceNaSemanaAnteriorAntesDaMeiaNoiteUtc() {
        assertEquals("2026-W30", PeriodoIsoWeek.from(Instant.parse("2026-07-26T23:59:59Z")));
    }

    @Test
    void iniciaNovaSemanaNaMeiaNoiteUtc() {
        assertEquals("2026-W31", PeriodoIsoWeek.from(Instant.parse("2026-07-27T00:00:00Z")));
    }

    @Test
    void rejeitaDataAusente() {
        assertThrows(DomainValidationException.class, () -> PeriodoIsoWeek.from(null));
    }
}
