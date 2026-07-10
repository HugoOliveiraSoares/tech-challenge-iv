package br.com.fiap.feedbackplatform.shared.port;

import br.com.fiap.feedbackplatform.shared.domain.Feedback;

public interface FeedbackRepository {
    void save(Feedback feedback);
}
