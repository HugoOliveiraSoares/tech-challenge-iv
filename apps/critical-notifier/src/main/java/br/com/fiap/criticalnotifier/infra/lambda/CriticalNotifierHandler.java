package br.com.fiap.criticalnotifier.infra.lambda;

import br.com.fiap.criticalnotifier.core.usecase.NotifyCriticalFeedbackUseCase;
import br.com.fiap.criticalnotifier.core.usecase.NotifyCriticalFeedbackUseCase.NotificationResult;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import jakarta.inject.Named;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

@Named("criticalNotifier")
public class CriticalNotifierHandler implements RequestHandler<SNSEvent, CriticalNotifierHandler.Output> {
    private static final Logger LOGGER = Logger.getLogger(CriticalNotifierHandler.class);

    private final SnsCriticalFeedbackEventParser eventParser;
    private final NotifyCriticalFeedbackUseCase notifyCriticalFeedbackUseCase;

    public CriticalNotifierHandler(
            SnsCriticalFeedbackEventParser eventParser,
            NotifyCriticalFeedbackUseCase notifyCriticalFeedbackUseCase) {
        this.eventParser = eventParser;
        this.notifyCriticalFeedbackUseCase = notifyCriticalFeedbackUseCase;
    }

    @Override
    public Output handleRequest(SNSEvent event, Context context) {
        if (event == null || event.getRecords() == null || event.getRecords().isEmpty()) {
            throw new DomainValidationException("Evento SNS sem registros.");
        }

        int sent = 0;
        int skipped = 0;

        for (SNSRecord rec : event.getRecords()) {
            CriticalFeedbackEvent criticalFeedbackEvent = parseRecord(rec);
            MDC.put("operation", "handle_sns_critical_feedback");
            MDC.put("feedbackId", criticalFeedbackEvent.feedbackId().toString());
            if (criticalFeedbackEvent.correlationId() != null) {
                MDC.put("correlationId", criticalFeedbackEvent.correlationId());
            }
            try {
                LOGGER.infof(
                        "Critical feedback SNS event received. feedbackId=%s correlationId=%s",
                        criticalFeedbackEvent.feedbackId(),
                        criticalFeedbackEvent.correlationId());

                NotificationResult result = notifyCriticalFeedbackUseCase.execute(criticalFeedbackEvent);
                if (result == NotificationResult.SENT) {
                    sent++;
                } else {
                    skipped++;
                }
            } finally {
                MDC.remove("operation");
                MDC.remove("feedbackId");
                MDC.remove("correlationId");
            }
        }

        return new Output("OK", sent, skipped);
    }

    private CriticalFeedbackEvent parseRecord(SNSRecord rec) {
        if (rec == null || rec.getSNS() == null || rec.getSNS().getMessage() == null) {
            throw new DomainValidationException("Registro SNS invalido.");
        }

        return eventParser.parse(rec.getSNS().getMessage());
    }

    public record Output(String status, int sent, int skipped) {
    }
}
