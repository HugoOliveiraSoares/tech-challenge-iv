package br.com.fiap.weeklyreport.core.gateway;

import br.com.fiap.weeklyreport.core.domain.WeeklyReportRequest;

public interface ReportEmailGateway {
    void sendWeeklyReport(WeeklyReportRequest request);
}
