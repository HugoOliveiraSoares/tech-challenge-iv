package br.com.fiap.feedbackapi.infra.gateway.sns;

import br.com.fiap.feedbackapi.core.domain.Feedback;
import br.com.fiap.feedbackapi.core.gateway.CriticalFeedbackPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NoOpCriticalFeedbackPublisher implements CriticalFeedbackPublisher {
    private static final Logger LOGGER = Logger.getLogger(NoOpCriticalFeedbackPublisher.class);

    @Override
    public void publish(Feedback feedback) {
        LOGGER.infof("Critical feedback publishing is not implemented yet. feedbackId=%s", feedback.id());
    }
}
