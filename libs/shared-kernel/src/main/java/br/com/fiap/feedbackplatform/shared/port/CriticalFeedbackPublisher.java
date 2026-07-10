package br.com.fiap.feedbackplatform.shared.port;

import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;

public interface CriticalFeedbackPublisher {
    void publish(CriticalFeedbackEvent event);
}
