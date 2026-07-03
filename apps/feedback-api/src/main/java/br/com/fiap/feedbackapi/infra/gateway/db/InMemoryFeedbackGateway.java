package br.com.fiap.feedbackapi.infra.gateway.db;

import br.com.fiap.feedbackapi.core.domain.Feedback;
import br.com.fiap.feedbackapi.core.gateway.FeedbackGateway;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ApplicationScoped
public class InMemoryFeedbackGateway implements FeedbackGateway {
    private final Queue<Feedback> feedbacks = new ConcurrentLinkedQueue<>();

    @Override
    public void save(Feedback feedback) {
        feedbacks.add(feedback);
    }
}
