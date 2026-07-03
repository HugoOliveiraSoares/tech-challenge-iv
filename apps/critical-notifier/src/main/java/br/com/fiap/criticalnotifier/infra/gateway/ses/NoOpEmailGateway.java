package br.com.fiap.criticalnotifier.infra.gateway.ses;

import br.com.fiap.criticalnotifier.core.domain.CriticalFeedbackNotification;
import br.com.fiap.criticalnotifier.core.gateway.EmailGateway;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NoOpEmailGateway implements EmailGateway {
    private static final Logger LOGGER = Logger.getLogger(NoOpEmailGateway.class);

    @Override
    public void sendCriticalFeedbackNotification(CriticalFeedbackNotification notification) {
        LOGGER.infof("Critical notification e-mail is not implemented yet. feedbackId=%s", notification.feedbackId());
    }
}
