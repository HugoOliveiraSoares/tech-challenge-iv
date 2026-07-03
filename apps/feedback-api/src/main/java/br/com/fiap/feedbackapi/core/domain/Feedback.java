package br.com.fiap.feedbackapi.core.domain;

import br.com.fiap.feedbackplatform.shared.Urgencia;
import java.time.Instant;
import java.util.UUID;

public record Feedback(
        UUID id,
        String descricao,
        int nota,
        Urgencia urgencia,
        Instant dataEnvio) {
}
