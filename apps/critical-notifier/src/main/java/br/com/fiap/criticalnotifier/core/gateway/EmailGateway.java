package br.com.fiap.criticalnotifier.core.gateway;

import br.com.fiap.criticalnotifier.core.domain.CriticalFeedbackNotification;

public interface EmailGateway {
    void sendCriticalFeedbackNotification(CriticalFeedbackNotification notification);
}
