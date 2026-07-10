package br.com.fiap.criticalnotifier.core.gateway;

import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;

public interface EmailGateway {
    void sendCriticalFeedbackNotification(CriticalFeedbackEvent event);
}
