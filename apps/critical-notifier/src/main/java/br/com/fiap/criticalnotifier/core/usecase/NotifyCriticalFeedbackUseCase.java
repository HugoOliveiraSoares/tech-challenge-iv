package br.com.fiap.criticalnotifier.core.usecase;

import br.com.fiap.criticalnotifier.core.domain.CriticalFeedbackNotification;
import br.com.fiap.criticalnotifier.core.gateway.EmailGateway;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotifyCriticalFeedbackUseCase {
    private final EmailGateway emailGateway;

    public NotifyCriticalFeedbackUseCase(EmailGateway emailGateway) {
        this.emailGateway = emailGateway;
    }

    public void execute(CriticalFeedbackNotification notification) {
        emailGateway.sendCriticalFeedbackNotification(notification);
    }
}
