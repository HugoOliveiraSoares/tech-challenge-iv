package br.com.fiap.criticalnotifier.core.gateway;

import java.util.UUID;

public interface NotificationIdempotencyGateway {
    boolean tryStart(UUID feedbackId);

    void markSent(UUID feedbackId);

    void markFailedBeforeSend(UUID feedbackId, String reason);

    void markFailedAfterSendAttempt(UUID feedbackId, String reason);
}
