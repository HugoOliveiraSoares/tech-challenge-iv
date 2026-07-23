package br.com.fiap.weeklyreport.core.gateway;

public interface WeeklyReportIdempotencyGateway {
    boolean tryStart(String periodo);

    void markSent(String periodo);

    void markFailedBeforeSend(String periodo, String reason);

    void markFailedAfterSendAttempt(String periodo, String reason);
}
