package br.com.fiap.feedbackapi.infra.gateway.db;

import br.com.fiap.feedbackplatform.shared.domain.Feedback;
import br.com.fiap.feedbackplatform.shared.port.FeedbackRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ApplicationScoped
public class InMemoryFeedbackGateway implements FeedbackRepository {
    private final Queue<Feedback> feedbacks = new ConcurrentLinkedQueue<>();

    @Override
    public void save(Feedback feedback) {
        feedbacks.add(feedback);
    }
}
