package br.com.fiap.criticalnotifier.infra.lambda;

import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class SnsCriticalFeedbackEventParser {
    private final ObjectMapper objectMapper;

    public SnsCriticalFeedbackEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CriticalFeedbackEvent parse(String message) {
        if (message == null || message.isBlank()) {
            throw new DomainValidationException("Mensagem SNS vazia.");
        }

        try {
            CriticalFeedbackEventPayload payload = objectMapper.readValue(message, CriticalFeedbackEventPayload.class);
            return payload.toDomain();
        } catch (DomainValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DomainValidationException("Mensagem SNS invalida.", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CriticalFeedbackEventPayload(
            String feedbackId,
            String correlationId,
            String descricao,
            Integer nota,
            Urgencia urgencia,
            Instant dataEnvio) {

        CriticalFeedbackEvent toDomain() {
            if (feedbackId == null || feedbackId.isBlank()) {
                throw new DomainValidationException("Campo feedbackId e obrigatorio.");
            }
            if (nota == null) {
                throw new DomainValidationException("Campo nota e obrigatorio.");
            }

            return new CriticalFeedbackEvent(
                    UUID.fromString(feedbackId.trim()),
                    correlationId,
                    descricao,
                    nota,
                    urgencia,
                    dataEnvio);
        }
    }
}
