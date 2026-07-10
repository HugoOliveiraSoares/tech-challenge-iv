package br.com.fiap.criticalnotifier.infra.gateway.ses;

import br.com.fiap.criticalnotifier.core.gateway.EmailGateway;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NoOpEmailGateway implements EmailGateway {
    private static final Logger LOGGER = Logger.getLogger(NoOpEmailGateway.class);

    @Override
    public void sendCriticalFeedbackNotification(CriticalFeedbackEvent event) {
        LOGGER.infof("Critical notification e-mail is not implemented yet. feedbackId=%s", event.feedbackId());
    }
}
