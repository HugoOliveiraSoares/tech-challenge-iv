package br.com.fiap.weeklyreport.core.usecase;

import br.com.fiap.feedbackplatform.shared.domain.PeriodoIsoWeek;
import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import br.com.fiap.weeklyreport.core.domain.WeeklyFeedback;
import br.com.fiap.weeklyreport.core.domain.WeeklyReport;
import br.com.fiap.weeklyreport.core.domain.WeeklyReportRequest;
import br.com.fiap.weeklyreport.core.domain.WeeklyReportResult;
import br.com.fiap.weeklyreport.core.gateway.ReportEmailGateway;
import br.com.fiap.weeklyreport.core.gateway.WeeklyFeedbackReader;
import br.com.fiap.weeklyreport.core.gateway.WeeklyReportIdempotencyGateway;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

@ApplicationScoped
public class GenerateWeeklyReportUseCase {
    private static final Logger LOGGER = Logger.getLogger(GenerateWeeklyReportUseCase.class);

    private final WeeklyFeedbackReader weeklyFeedbackReader;
    private final ReportEmailGateway reportEmailGateway;
    private final WeeklyReportIdempotencyGateway idempotencyGateway;
    private final Clock clock;

    public GenerateWeeklyReportUseCase(
            WeeklyFeedbackReader weeklyFeedbackReader,
            ReportEmailGateway reportEmailGateway,
            WeeklyReportIdempotencyGateway idempotencyGateway,
            Clock clock) {
        this.weeklyFeedbackReader = weeklyFeedbackReader;
        this.reportEmailGateway = reportEmailGateway;
        this.idempotencyGateway = idempotencyGateway;
        this.clock = clock;
    }

    public WeeklyReportResult execute(WeeklyReportRequest request) {
        String periodo = resolvePeriodo(request);
        MDC.put("operation", "generate_weekly_report");
        MDC.put("periodo", periodo);
        MDC.put("status", "started");
        try {
            LOGGER.infof("Weekly report job started. periodo=%s", periodo);

            if (!idempotencyGateway.tryStart(periodo)) {
                MDC.put("status", "skipped");
                LOGGER.infof("Weekly report already processed or in progress. periodo=%s", periodo);
                return new WeeklyReportResult(periodo, false, "SKIPPED");
            }

            try {
                List<WeeklyFeedback> feedbacks = weeklyFeedbackReader.findByPeriodo(periodo).stream()
                        .sorted(Comparator.comparing(WeeklyFeedback::dataEnvio))
                        .toList();
                MDC.put("status", "records_processed");
                MDC.put("feedback_count", feedbacks.size());
                LOGGER.infof("Weekly report records processed. periodo=%s count=%d", periodo, feedbacks.size());
                if (feedbacks.isEmpty()) {
                    LOGGER.infof("No feedback found for weekly report. periodo=%s", periodo);
                }

                WeeklyReport report = buildReport(periodo, feedbacks);
                reportEmailGateway.sendWeeklyReport(report);
                idempotencyGateway.markSent(periodo);
                MDC.put("status", "sent");
                LOGGER.infof("Weekly report sent. periodo=%s", periodo);
                return new WeeklyReportResult(periodo, true, "SENT");
            } catch (RuntimeException exception) {
                MDC.put("status", "failed");
                try {
                    idempotencyGateway.markFailed(periodo, exception.getMessage());
                } catch (RuntimeException markFailedException) {
                    LOGGER.errorf(markFailedException, "Failed to mark weekly report as failed. periodo=%s", periodo);
                }
                LOGGER.errorf(exception, "Weekly report failed. periodo=%s", periodo);
                throw exception;
            }
        } finally {
            clearMdc();
        }
    }

    private void clearMdc() {
        MDC.remove("operation");
        MDC.remove("periodo");
        MDC.remove("status");
        MDC.remove("feedback_count");
    }

    private String resolvePeriodo(WeeklyReportRequest request) {
        if (request != null && request.periodo() != null && !request.periodo().isBlank()) {
            return request.periodo().trim();
        }

        return PeriodoIsoWeek.from(clock.instant());
    }

    private WeeklyReport buildReport(String periodo, List<WeeklyFeedback> feedbacks) {
        double mediaGeral = feedbacks.isEmpty()
                ? 0.0
                : feedbacks.stream().mapToInt(WeeklyFeedback::nota).average().orElse(0.0);

        Map<LocalDate, Long> quantidadePorDia = initialDays(periodo);
        feedbacks.stream()
                .collect(Collectors.groupingBy(
                        feedback -> LocalDate.ofInstant(feedback.dataEnvio(), ZoneOffset.UTC),
                        Collectors.counting()))
                .forEach(quantidadePorDia::put);

        Map<Urgencia, Long> quantidadePorUrgencia = new EnumMap<>(Urgencia.class);
        for (Urgencia urgencia : Urgencia.values()) {
            quantidadePorUrgencia.put(urgencia, 0L);
        }
        feedbacks.stream()
                .collect(Collectors.groupingBy(WeeklyFeedback::urgencia, () -> new EnumMap<>(Urgencia.class), Collectors.counting()))
                .forEach(quantidadePorUrgencia::put);

        List<WeeklyFeedback> feedbacksCriticos = feedbacks.stream()
                .filter(feedback -> feedback.urgencia() == Urgencia.CRITICA)
                .toList();

        return new WeeklyReport(
                periodo,
                mediaGeral,
                quantidadePorDia,
                quantidadePorUrgencia,
                feedbacks,
                feedbacksCriticos);
    }

    private Map<LocalDate, Long> initialDays(String periodo) {
        int weekBasedYear = Integer.parseInt(periodo.substring(0, 4));
        int week = Integer.parseInt(periodo.substring(6));
        WeekFields weekFields = WeekFields.ISO;
        LocalDate monday = LocalDate.now(clock)
                .with(weekFields.weekBasedYear(), weekBasedYear)
                .with(weekFields.weekOfWeekBasedYear(), week)
                .with(DayOfWeek.MONDAY);

        Map<LocalDate, Long> days = new LinkedHashMap<>();
        for (int index = 0; index < 7; index++) {
            days.put(monday.plusDays(index), 0L);
        }
        return days;
    }
}
