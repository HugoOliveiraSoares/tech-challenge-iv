package br.com.fiap.feedbackplatform.shared.domain;

import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;

public final class PeriodoIsoWeek {
    private PeriodoIsoWeek() {
    }

    public static String from(Instant dataEnvio) {
        if (dataEnvio == null) {
            throw new DomainValidationException("Data de envio e obrigatoria.");
        }

        ZonedDateTime data = dataEnvio.atZone(ZoneOffset.UTC);
        int year = data.get(WeekFields.ISO.weekBasedYear());
        int week = data.get(WeekFields.ISO.weekOfWeekBasedYear());

        return "%04d-W%02d".formatted(year, week);
    }
}
