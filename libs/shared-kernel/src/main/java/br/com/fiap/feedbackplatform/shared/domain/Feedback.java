package br.com.fiap.feedbackplatform.shared.domain;

import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import java.time.Instant;
import java.util.UUID;

public record Feedback(
        UUID id,
        String descricao,
        int nota,
        Urgencia urgencia,
        Instant dataEnvio,
        String periodo,
        String correlationId) {

    public Feedback {
        validarId(id);
        descricao = validarDescricao(descricao);

        Urgencia urgenciaEsperada = UrgenciaClassifier.classify(nota);
        if (urgencia == null) {
            throw new DomainValidationException("Urgencia e obrigatoria.");
        }
        if (urgencia != urgenciaEsperada) {
            throw new DomainValidationException("Urgencia nao corresponde a nota informada.");
        }

        String periodoEsperado = PeriodoIsoWeek.from(dataEnvio);
        if (!periodoEsperado.equals(periodo)) {
            throw new DomainValidationException("Periodo nao corresponde a data de envio informada.");
        }

        correlationId = normalizarCorrelationId(correlationId);
    }

    public static Feedback criar(
            UUID id,
            String descricao,
            int nota,
            Instant dataEnvio,
            String correlationId) {
        validarId(id);
        String descricaoNormalizada = validarDescricao(descricao);
        Urgencia urgencia = UrgenciaClassifier.classify(nota);
        String periodo = PeriodoIsoWeek.from(dataEnvio);

        return new Feedback(
                id,
                descricaoNormalizada,
                nota,
                urgencia,
                dataEnvio,
                periodo,
                normalizarCorrelationId(correlationId));
    }

    private static void validarId(UUID id) {
        if (id == null) {
            throw new DomainValidationException("Id do feedback é obrigatório.");
        }
    }

    private static String validarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new DomainValidationException("Descrição é obrigatória.");
        }

        String descricaoNormalizada = descricao.trim();
        if (descricaoNormalizada.length() < 10) {
            throw new DomainValidationException("Descrição deve ter pelo menos 10 caracteres.");
        }

        if (descricaoNormalizada.length() > 1000) {
            throw new DomainValidationException("Descrição deve ter no máximo 1000 caracteres.");
        }

        return descricaoNormalizada;
    }

    private static String normalizarCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return null;
        }

        return correlationId.trim();
    }
}
