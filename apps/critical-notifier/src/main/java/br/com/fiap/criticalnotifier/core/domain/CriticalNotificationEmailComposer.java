package br.com.fiap.criticalnotifier.core.domain;

import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class CriticalNotificationEmailComposer {
    private static final DateTimeFormatter DATA_ENVIO_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    public CriticalNotificationEmail compose(CriticalFeedbackEvent event) {
        String subject = buildSubject(event);
        String body = buildBody(event);
        return new CriticalNotificationEmail(subject, body);
    }

    private String buildSubject(CriticalFeedbackEvent event) {
        String correlationSuffix = event.correlationId() == null ? "" : " | correlationId=" + event.correlationId();
        return "ALERTA: Feedback critico recebido | feedbackId=" + event.feedbackId() + correlationSuffix;
    }

    private String buildBody(CriticalFeedbackEvent event) {
        StringBuilder body = new StringBuilder();
        body.append("Aviso de urgencia - Feedback critico\n\n");
        body.append("Descricao: ").append(event.descricao()).append("\n");
        body.append("Urgencia: ").append(event.urgencia()).append("\n");
        body.append("Data de envio: ").append(DATA_ENVIO_FORMAT.format(event.dataEnvio())).append("\n\n");
        body.append("Rastreabilidade operacional\n");
        body.append("- feedbackId: ").append(event.feedbackId()).append("\n");
        body.append("- nota: ").append(event.nota()).append("\n");
        if (event.correlationId() != null) {
            body.append("- correlationId: ").append(event.correlationId()).append("\n");
        }
        return body.toString();
    }
}
