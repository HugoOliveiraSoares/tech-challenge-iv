package br.com.fiap.feedbackapi.core.gateway;

import br.com.fiap.feedbackapi.core.domain.Feedback;

public interface CriticalFeedbackPublisher {
    void publish(Feedback feedback);
}
