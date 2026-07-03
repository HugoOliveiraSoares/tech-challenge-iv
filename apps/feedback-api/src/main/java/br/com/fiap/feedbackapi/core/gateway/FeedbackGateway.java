package br.com.fiap.feedbackapi.core.gateway;

import br.com.fiap.feedbackapi.core.domain.Feedback;

public interface FeedbackGateway {
    void save(Feedback feedback);
}
