package br.com.fiap.criticalnotifier.infra.gateway.ses;

import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmail;
import br.com.fiap.criticalnotifier.core.exception.EmailSendAmbiguousException;
import br.com.fiap.criticalnotifier.core.exception.EmailSendRetryableException;
import br.com.fiap.criticalnotifier.core.gateway.EmailGateway;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.SocketTimeoutException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

@ApplicationScoped
public class SesCriticalEmailGateway implements EmailGateway {
    private static final Logger LOGGER = Logger.getLogger(SesCriticalEmailGateway.class);

    private final SesClient sesClient;
    private final String adminEmailTo;
    private final String emailFrom;

    public SesCriticalEmailGateway(
            SesClient sesClient,
            @ConfigProperty(name = "ADMIN_EMAIL_TO") String adminEmailTo,
            @ConfigProperty(name = "EMAIL_FROM") String emailFrom) {
        this.sesClient = sesClient;
        this.adminEmailTo = adminEmailTo;
        this.emailFrom = emailFrom;
    }

    @Override
    public void sendCriticalNotification(CriticalNotificationEmail email) {
        MdcSnapshot mdcSnapshot = MdcSnapshot.capture();
        MDC.put("operation", "send_critical_notification_email");
        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                    .source(emailFrom)
                    .destination(Destination.builder().toAddresses(adminEmailTo).build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .charset("UTF-8")
                                    .data(email.subject())
                                    .build())
                            .body(Body.builder()
                                    .text(Content.builder()
                                            .charset("UTF-8")
                                            .data(email.body())
                                            .build())
                                    .build())
                            .build())
                    .build());
        } catch (SesException exception) {
            LOGGER.errorf(
                    exception,
                    "Failed to send critical notification e-mail. feedbackId=%s correlationId=%s",
                    MDC.get("feedbackId"),
                    MDC.get("correlationId"));
            if (isRetryableSesFailure(exception)) {
                throw new EmailSendRetryableException(
                        "SES rejected the request before acceptance.", exception);
            }
            throw new EmailSendAmbiguousException("SES returned an indeterminate failure.", exception);
        } catch (SdkClientException exception) {
            LOGGER.errorf(
                    exception,
                    "Failed to send critical notification e-mail. feedbackId=%s correlationId=%s",
                    MDC.get("feedbackId"),
                    MDC.get("correlationId"));
            if (isTimeout(exception)) {
                throw new EmailSendAmbiguousException(
                        "SES request timed out with indeterminate result.", exception);
            }
            throw new EmailSendRetryableException("SES client failed before acceptance.", exception);
        } finally {
            mdcSnapshot.restore();
        }
    }

    private static boolean isRetryableSesFailure(SesException exception) {
        Integer statusCode = exception.statusCode();
        if (statusCode == null) {
            return false;
        }
        return statusCode == 429 || statusCode >= 500;
    }

    private static boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            if (current instanceof IOException && current.getMessage() != null
                    && current.getMessage().toLowerCase().contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return exception.getMessage() != null && exception.getMessage().toLowerCase().contains("timeout");
    }

    private record MdcSnapshot(Object operation, Object feedbackId, Object correlationId) {
        static MdcSnapshot capture() {
            return new MdcSnapshot(MDC.get("operation"), MDC.get("feedbackId"), MDC.get("correlationId"));
        }

        void restore() {
            restore("operation", operation);
            restore("feedbackId", feedbackId);
            restore("correlationId", correlationId);
        }

        private void restore(String key, Object value) {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        }
    }
}
