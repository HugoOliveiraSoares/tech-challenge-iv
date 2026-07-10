package br.com.fiap.feedbackapi.core.usecase;

import br.com.fiap.feedbackapi.core.dto.CriarAvaliacaoCommand;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.domain.Feedback;
import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import br.com.fiap.feedbackplatform.shared.port.CriticalFeedbackPublisher;
import br.com.fiap.feedbackplatform.shared.port.FeedbackRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class CriarAvaliacaoUseCase {
    private final FeedbackRepository feedbackRepository;
    private final CriticalFeedbackPublisher criticalFeedbackPublisher;
    private final Clock clock;

    public CriarAvaliacaoUseCase(
            FeedbackRepository feedbackRepository,
            CriticalFeedbackPublisher criticalFeedbackPublisher,
            Clock clock) {
        this.feedbackRepository = feedbackRepository;
        this.criticalFeedbackPublisher = criticalFeedbackPublisher;
        this.clock = clock;
    }

    public Feedback execute(CriarAvaliacaoCommand command) {
        Feedback feedback = Feedback.criar(
                UUID.randomUUID(),
                command.descricao(),
                command.nota(),
                Instant.now(clock),
                command.correlationId());

        feedbackRepository.save(feedback);
        if (feedback.urgencia() == Urgencia.CRITICA) {
            criticalFeedbackPublisher.publish(CriticalFeedbackEvent.from(feedback));
        }

        return feedback;
    }
}
