package br.com.fiap.criticalnotifier.core.usecase;

import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmail;
import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmailComposer;
import br.com.fiap.criticalnotifier.core.gateway.EmailGateway;
import br.com.fiap.criticalnotifier.core.gateway.NotificationIdempotencyGateway;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import jakarta.enterprise.context.ApplicationScoped;
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
            if (!idempotencyGateway.tryStart(event.feedbackId())) {
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
                sendAttempted = true;
                emailGateway.sendCriticalNotification(email);
                idempotencyGateway.markSent(event.feedbackId());
                MDC.put("status", "sent");
                LOGGER.infof(
                        "Critical notification e-mail sent. feedbackId=%s correlationId=%s",
                        event.feedbackId(),
                        event.correlationId());
                return NotificationResult.SENT;
            } catch (RuntimeException exception) {
                MDC.put("status", "failed");
                try {
                    if (sendAttempted) {
                        idempotencyGateway.markFailedAfterSendAttempt(event.feedbackId(), exception.getMessage());
                    } else {
                        idempotencyGateway.markFailedBeforeSend(event.feedbackId(), exception.getMessage());
                    }
                } catch (RuntimeException markFailedException) {
                    LOGGER.errorf(
                            markFailedException,
                            "Failed to mark critical notification as failed. feedbackId=%s",
                            event.feedbackId());
                }
                LOGGER.errorf(
                        exception,
                        "Critical notification failed. feedbackId=%s correlationId=%s",
                        event.feedbackId(),
                        event.correlationId());
                throw exception;
            }
        } finally {
            clearMdc();
        }
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
