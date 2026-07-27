package br.com.fiap.criticalnotifier.core.gateway;

import br.com.fiap.criticalnotifier.core.domain.ProcessingLease;
import java.util.Optional;
import java.util.UUID;

public interface NotificationIdempotencyGateway {
    Optional<ProcessingLease> tryStart(UUID feedbackId);

    void markAboutToSend(UUID feedbackId, ProcessingLease lease);

    void markSent(UUID feedbackId, ProcessingLease lease);

    void markFailedBeforeSend(UUID feedbackId, ProcessingLease lease, String reason);

    void markFailedAfterSendAttempt(UUID feedbackId, ProcessingLease lease, String reason);
}
