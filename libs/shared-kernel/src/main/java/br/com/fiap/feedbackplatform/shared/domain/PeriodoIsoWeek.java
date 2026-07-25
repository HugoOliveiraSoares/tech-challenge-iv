package br.com.fiap.feedbackplatform.shared.domain;

import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;

public final class PeriodoIsoWeek {
    private PeriodoIsoWeek() {
    }

    public static String from(Instant dataEnvio) {
        return from(dataEnvio, ZoneOffset.UTC);
    }

    public static String from(Instant dataEnvio, ZoneId zoneId) {
        if (dataEnvio == null) {
            throw new DomainValidationException("Data de envio e obrigatoria.");
        }
        if (zoneId == null) {
            throw new DomainValidationException("Timezone e obrigatorio.");
        }

        ZonedDateTime data = dataEnvio.atZone(zoneId);
        int year = data.get(WeekFields.ISO.weekBasedYear());
        int week = data.get(WeekFields.ISO.weekOfWeekBasedYear());

        return "%04d-W%02d".formatted(year, week);
    }
}
