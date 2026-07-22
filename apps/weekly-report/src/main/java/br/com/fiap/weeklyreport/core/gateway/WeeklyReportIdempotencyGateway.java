package br.com.fiap.weeklyreport.core.gateway;

public interface WeeklyReportIdempotencyGateway {
    boolean tryStart(String periodo);

    void markSent(String periodo);

    void markFailed(String periodo, String reason);
}
