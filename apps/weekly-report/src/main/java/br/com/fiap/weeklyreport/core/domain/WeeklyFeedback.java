package br.com.fiap.weeklyreport.core.domain;

import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import java.time.Instant;
import java.util.UUID;

public record WeeklyFeedback(
        UUID id,
        String descricao,
        int nota,
        Urgencia urgencia,
        Instant dataEnvio) {
}
