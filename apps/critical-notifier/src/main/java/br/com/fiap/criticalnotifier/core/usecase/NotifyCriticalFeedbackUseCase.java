package br.com.fiap.criticalnotifier.core.usecase;

import br.com.fiap.criticalnotifier.core.gateway.EmailGateway;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotifyCriticalFeedbackUseCase {
    private final EmailGateway emailGateway;

    public NotifyCriticalFeedbackUseCase(EmailGateway emailGateway) {
        this.emailGateway = emailGateway;
    }

    public void execute(CriticalFeedbackEvent event) {
        emailGateway.sendCriticalFeedbackNotification(event);
    }
}
