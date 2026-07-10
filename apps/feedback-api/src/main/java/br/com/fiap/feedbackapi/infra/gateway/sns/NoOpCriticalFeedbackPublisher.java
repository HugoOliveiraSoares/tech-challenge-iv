package br.com.fiap.feedbackapi.infra.gateway.sns;

import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.port.CriticalFeedbackPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NoOpCriticalFeedbackPublisher implements CriticalFeedbackPublisher {
    private static final Logger LOGGER = Logger.getLogger(NoOpCriticalFeedbackPublisher.class);

    @Override
    public void publish(CriticalFeedbackEvent event) {
        LOGGER.infof("Critical feedback publishing is not implemented yet. feedbackId=%s", event.feedbackId());
    }
}
