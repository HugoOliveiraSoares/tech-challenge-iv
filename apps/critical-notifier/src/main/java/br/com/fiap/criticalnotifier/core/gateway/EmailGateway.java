package br.com.fiap.criticalnotifier.core.gateway;

import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmail;

public interface EmailGateway {
    void sendCriticalNotification(CriticalNotificationEmail email);
}
