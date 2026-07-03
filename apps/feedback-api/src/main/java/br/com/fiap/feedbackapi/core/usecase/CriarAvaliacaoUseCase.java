package br.com.fiap.feedbackapi.core.usecase;

import br.com.fiap.feedbackapi.core.domain.Feedback;
import br.com.fiap.feedbackapi.core.dto.CriarAvaliacaoCommand;
import br.com.fiap.feedbackapi.core.gateway.CriticalFeedbackPublisher;
import br.com.fiap.feedbackapi.core.gateway.FeedbackGateway;
import br.com.fiap.feedbackplatform.shared.Urgencia;
import br.com.fiap.feedbackplatform.shared.UrgenciaClassifier;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class CriarAvaliacaoUseCase {
    private final FeedbackGateway feedbackGateway;
    private final CriticalFeedbackPublisher criticalFeedbackPublisher;
    private final Clock clock;

    public CriarAvaliacaoUseCase(
            FeedbackGateway feedbackGateway,
            CriticalFeedbackPublisher criticalFeedbackPublisher,
            Clock clock) {
        this.feedbackGateway = feedbackGateway;
        this.criticalFeedbackPublisher = criticalFeedbackPublisher;
        this.clock = clock;
    }

    public Feedback execute(CriarAvaliacaoCommand command) {
        Urgencia urgencia = UrgenciaClassifier.classify(command.nota());
        Feedback feedback = new Feedback(
                UUID.randomUUID(),
                command.descricao(),
                command.nota(),
                urgencia,
                Instant.now(clock));

        feedbackGateway.save(feedback);
        if (urgencia == Urgencia.CRITICA) {
            criticalFeedbackPublisher.publish(feedback);
        }

        return feedback;
    }
}
