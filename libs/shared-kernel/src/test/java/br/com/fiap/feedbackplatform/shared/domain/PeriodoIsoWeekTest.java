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
    void rejeitaDataAusente() {
        assertThrows(DomainValidationException.class, () -> PeriodoIsoWeek.from(null));
    }
}
