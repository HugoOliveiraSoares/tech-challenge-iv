package br.com.fiap.criticalnotifier.core.usecase;

import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmail;
import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmailComposer;
import br.com.fiap.criticalnotifier.core.domain.ProcessingLease;
import br.com.fiap.criticalnotifier.core.exception.EmailSendAmbiguousException;
import br.com.fiap.criticalnotifier.core.exception.EmailSendRetryableException;
import br.com.fiap.criticalnotifier.core.gateway.EmailGateway;
import br.com.fiap.criticalnotifier.core.gateway.NotificationIdempotencyGateway;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

@ApplicationScoped
public class NotifyCriticalFeedbackUseCase {
    private static final Logger LOGGER = Logger.getLogger(NotifyCriticalFeedbackUseCase.class);

    private final NotificationIdempotencyGateway idempotencyGateway;
    private final CriticalNotificationEmailComposer emailComposer;
    private final EmailGateway emailGateway;

    public NotifyCriticalFeedbackUseCase(
            NotificationIdempotencyGateway idempotencyGateway,
            CriticalNotificationEmailComposer emailComposer,
            EmailGateway emailGateway) {
        this.idempotencyGateway = idempotencyGateway;
        this.emailComposer = emailComposer;
        this.emailGateway = emailGateway;
    }

    public NotificationResult execute(CriticalFeedbackEvent event) {
        MDC.put("operation", "notify_critical_feedback");
        MDC.put("feedbackId", event.feedbackId().toString());
        if (event.correlationId() != null) {
            MDC.put("correlationId", event.correlationId());
        }
        MDC.put("status", "started");

        try {
            Optional<ProcessingLease> lease = idempotencyGateway.tryStart(event.feedbackId());
            if (lease.isEmpty()) {
                MDC.put("status", "skipped");
                LOGGER.infof(
                        "Critical notification already processed or in progress. feedbackId=%s correlationId=%s",
                        event.feedbackId(),
                        event.correlationId());
                return NotificationResult.SKIPPED;
            }

            boolean sendAttempted = false;
            try {
                CriticalNotificationEmail email = emailComposer.compose(event);
                idempotencyGateway.markAboutToSend(event.feedbackId(), lease.get());
                sendAttempted = true;
                emailGateway.sendCriticalNotification(email);
                idempotencyGateway.markSent(event.feedbackId(), lease.get());
                MDC.put("status", "sent");
                LOGGER.infof(
                        "Critical notification e-mail sent. feedbackId=%s correlationId=%s",
                        event.feedbackId(),
                        event.correlationId());
                return NotificationResult.SENT;
            } catch (EmailSendRetryableException exception) {
                markFailure(event, lease.get(), true, exception.getMessage());
                throw exception;
            } catch (EmailSendAmbiguousException exception) {
                markFailure(event, lease.get(), false, exception.getMessage());
                throw exception;
            } catch (RuntimeException exception) {
                markFailure(event, lease.get(), !sendAttempted, exception.getMessage());
                throw exception;
            }
        } finally {
            clearMdc();
        }
    }

    private void markFailure(
            CriticalFeedbackEvent event, ProcessingLease lease, boolean retryable, String reason) {
        MDC.put("status", "failed");
        try {
            if (retryable) {
                idempotencyGateway.markFailedBeforeSend(event.feedbackId(), lease, reason);
            } else {
                idempotencyGateway.markFailedAfterSendAttempt(event.feedbackId(), lease, reason);
            }
        } catch (RuntimeException markFailedException) {
            LOGGER.errorf(
                    markFailedException,
                    "Failed to mark critical notification as failed. feedbackId=%s",
                    event.feedbackId());
        }
        LOGGER.errorf(
                "Critical notification failed. feedbackId=%s correlationId=%s reason=%s retryable=%s",
                event.feedbackId(),
                event.correlationId(),
                reason,
                retryable);
    }

    private void clearMdc() {
        MDC.remove("operation");
        MDC.remove("feedbackId");
        MDC.remove("correlationId");
        MDC.remove("status");
    }

    public enum NotificationResult {
        SENT,
        SKIPPED
    }
}
