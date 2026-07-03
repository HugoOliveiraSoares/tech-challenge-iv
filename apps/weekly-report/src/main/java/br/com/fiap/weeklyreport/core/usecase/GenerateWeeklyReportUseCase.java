package br.com.fiap.weeklyreport.core.usecase;

import br.com.fiap.weeklyreport.core.domain.WeeklyReportRequest;
import br.com.fiap.weeklyreport.core.gateway.ReportEmailGateway;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GenerateWeeklyReportUseCase {
    private final ReportEmailGateway reportEmailGateway;

    public GenerateWeeklyReportUseCase(ReportEmailGateway reportEmailGateway) {
        this.reportEmailGateway = reportEmailGateway;
    }

    public void execute(WeeklyReportRequest request) {
        reportEmailGateway.sendWeeklyReport(request);
    }
}
