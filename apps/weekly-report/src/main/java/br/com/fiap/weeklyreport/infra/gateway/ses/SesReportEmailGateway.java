package br.com.fiap.weeklyreport.infra.gateway.ses;

import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import br.com.fiap.weeklyreport.core.domain.WeeklyFeedback;
import br.com.fiap.weeklyreport.core.domain.WeeklyReport;
import br.com.fiap.weeklyreport.core.gateway.ReportEmailGateway;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

@ApplicationScoped
public class SesReportEmailGateway implements ReportEmailGateway {
    private static final Logger LOGGER = Logger.getLogger(SesReportEmailGateway.class);

    private final SesClient sesClient;
    private final String adminEmailTo;
    private final String emailFrom;

    public SesReportEmailGateway(
            SesClient sesClient,
            @ConfigProperty(name = "ADMIN_EMAIL_TO") String adminEmailTo,
            @ConfigProperty(name = "EMAIL_FROM") String emailFrom) {
        this.sesClient = sesClient;
        this.adminEmailTo = adminEmailTo;
        this.emailFrom = emailFrom;
    }

    @Override
    public void sendWeeklyReport(WeeklyReport report) {
        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                    .source(emailFrom)
                    .destination(Destination.builder().toAddresses(adminEmailTo).build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .charset("UTF-8")
                                    .data("Relatorio semanal de feedbacks - " + report.periodo())
                                    .build())
                            .body(Body.builder()
                                    .text(Content.builder()
                                            .charset("UTF-8")
                                            .data(buildBody(report))
                                            .build())
                                    .build())
                            .build())
                    .build());
        } catch (RuntimeException exception) {
            LOGGER.errorf(exception, "Failed to send weekly report e-mail. periodo=%s", report.periodo());
            throw exception;
        }
    }

    private String buildBody(WeeklyReport report) {
        StringBuilder body = new StringBuilder();
        body.append("Relatorio semanal de feedbacks\n");
        body.append("Periodo: ").append(report.periodo()).append("\n");
        body.append("Media semanal: ").append(String.format(Locale.ROOT, "%.2f", report.mediaGeral())).append("\n\n");

        body.append("Quantidade por dia\n");
        for (Map.Entry<LocalDate, Long> entry : report.quantidadePorDia().entrySet()) {
            body.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        body.append("\nQuantidade por urgencia\n");
        for (Urgencia urgencia : Urgencia.values()) {
            body.append("- ").append(urgencia).append(": ")
                    .append(report.quantidadePorUrgencia().getOrDefault(urgencia, 0L))
                    .append("\n");
        }

        body.append("\nFeedbacks resumidos\n");
        if (report.feedbacks().isEmpty()) {
            body.append("Nenhum feedback encontrado na semana.\n");
        } else {
            report.feedbacks().forEach(feedback -> appendFeedback(body, feedback));
        }

        body.append("\nFeedbacks criticos\n");
        if (report.feedbacksCriticos().isEmpty()) {
            body.append("Nenhum feedback critico encontrado na semana.\n");
        } else {
            report.feedbacksCriticos().forEach(feedback -> appendFeedback(body, feedback));
        }

        return body.toString();
    }

    private void appendFeedback(StringBuilder body, WeeklyFeedback feedback) {
        body.append("- ")
                .append(feedback.dataEnvio())
                .append(" | ")
                .append(feedback.urgencia())
                .append(" | ")
                .append(feedback.descricao())
                .append("\n");
    }
}
