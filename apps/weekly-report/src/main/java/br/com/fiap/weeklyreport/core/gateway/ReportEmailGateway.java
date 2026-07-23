package br.com.fiap.weeklyreport.core.gateway;

import br.com.fiap.weeklyreport.core.domain.WeeklyReport;

public interface ReportEmailGateway {
    void sendWeeklyReport(WeeklyReport report);
}
