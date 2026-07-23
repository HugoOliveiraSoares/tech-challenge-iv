package br.com.fiap.weeklyreport.core.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import br.com.fiap.weeklyreport.core.domain.WeeklyFeedback;
import br.com.fiap.weeklyreport.core.domain.WeeklyReport;
import br.com.fiap.weeklyreport.core.domain.WeeklyReportRequest;
import br.com.fiap.weeklyreport.core.domain.WeeklyReportResult;
import br.com.fiap.weeklyreport.core.gateway.WeeklyReportIdempotencyGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerateWeeklyReportUseCaseTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void calculaAgregacoesComMassaControlada() {
        List<WeeklyFeedback> feedbacks = List.of(
                feedback("2026-06-22T10:00:00Z", 2, Urgencia.CRITICA),
                feedback("2026-06-22T12:00:00Z", 8, Urgencia.BAIXA),
                feedback("2026-06-23T10:00:00Z", 5, Urgencia.MEDIA));
        List<WeeklyReport> sent = new ArrayList<>();
        GenerateWeeklyReportUseCase useCase = newUseCase(feedbacks, sent, new InMemoryIdempotencyGateway());

        WeeklyReportResult result = useCase.execute(new WeeklyReportRequest("2026-W26"));

        assertTrue(result.sent());
        assertEquals("SENT", result.status());
        assertEquals(1, sent.size());
        WeeklyReport report = sent.getFirst();
        assertEquals("2026-W26", report.periodo());
        assertEquals(5.0, report.mediaGeral());
        assertEquals(2L, report.quantidadePorDia().get(LocalDate.parse("2026-06-22")));
        assertEquals(1L, report.quantidadePorDia().get(LocalDate.parse("2026-06-23")));
        assertEquals(0L, report.quantidadePorDia().get(LocalDate.parse("2026-06-24")));
        assertEquals(1L, report.quantidadePorUrgencia().get(Urgencia.CRITICA));
        assertEquals(1L, report.quantidadePorUrgencia().get(Urgencia.MEDIA));
        assertEquals(1L, report.quantidadePorUrgencia().get(Urgencia.BAIXA));
        assertEquals(3, report.feedbacks().size());
        assertEquals(1, report.feedbacksCriticos().size());
    }

    @Test
    void enviaRelatorioMesmoSemFeedbacks() {
        List<WeeklyReport> sent = new ArrayList<>();
        GenerateWeeklyReportUseCase useCase = newUseCase(List.of(), sent, new InMemoryIdempotencyGateway());

        WeeklyReportResult result = useCase.execute(new WeeklyReportRequest("2026-W26"));

        assertTrue(result.sent());
        WeeklyReport report = sent.getFirst();
        assertEquals(0.0, report.mediaGeral());
        assertEquals(7, report.quantidadePorDia().size());
        assertTrue(report.quantidadePorDia().values().stream().allMatch(count -> count == 0L));
        assertEquals(0L, report.quantidadePorUrgencia().get(Urgencia.CRITICA));
        assertEquals(0L, report.quantidadePorUrgencia().get(Urgencia.MEDIA));
        assertEquals(0L, report.quantidadePorUrgencia().get(Urgencia.BAIXA));
        assertTrue(report.feedbacks().isEmpty());
        assertTrue(report.feedbacksCriticos().isEmpty());
    }

    @Test
    void naoEnviaRelatorioDuplicadoParaMesmoPeriodo() {
        List<WeeklyReport> sent = new ArrayList<>();
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        GenerateWeeklyReportUseCase useCase = newUseCase(
                List.of(feedback("2026-06-22T10:00:00Z", 2, Urgencia.CRITICA)),
                sent,
                idempotencyGateway);

        WeeklyReportResult first = useCase.execute(new WeeklyReportRequest("2026-W26"));
        WeeklyReportResult second = useCase.execute(new WeeklyReportRequest("2026-W26"));

        assertTrue(first.sent());
        assertFalse(second.sent());
        assertEquals("SKIPPED", second.status());
        assertEquals(1, sent.size());
    }

    @Test
    void permiteReprocessarPeriodoDepoisDeFalhaAntesDoEnvio() {
        List<WeeklyReport> sent = new ArrayList<>();
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        int[] attempts = {0};
        GenerateWeeklyReportUseCase useCase = new GenerateWeeklyReportUseCase(
                periodo -> {
                    attempts[0]++;
                    if (attempts[0] == 1) {
                        throw new IllegalStateException("DynamoDB indisponivel");
                    }
                    return List.of(feedback("2026-06-22T10:00:00Z", 2, Urgencia.CRITICA));
                },
                sent::add,
                idempotencyGateway,
                CLOCK);

        assertThrows(IllegalStateException.class, () -> useCase.execute(new WeeklyReportRequest("2026-W26")));
        WeeklyReportResult retry = useCase.execute(new WeeklyReportRequest("2026-W26"));

        assertTrue(retry.sent());
        assertEquals("SENT", retry.status());
        assertEquals(2, attempts[0]);
        assertEquals(1, sent.size());
    }

    @Test
    void naoReprocessaPeriodoDepoisDeFalhaAmbiguaNoEnvio() {
        List<WeeklyReport> sent = new ArrayList<>();
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        int[] sendAttempts = {0};
        GenerateWeeklyReportUseCase useCase = new GenerateWeeklyReportUseCase(
                periodo -> List.of(feedback("2026-06-22T10:00:00Z", 2, Urgencia.CRITICA)),
                report -> {
                    sendAttempts[0]++;
                    sent.add(report);
                    throw new IllegalStateException("timeout depois da tentativa de envio");
                },
                idempotencyGateway,
                CLOCK);

        assertThrows(IllegalStateException.class, () -> useCase.execute(new WeeklyReportRequest("2026-W26")));
        WeeklyReportResult retry = useCase.execute(new WeeklyReportRequest("2026-W26"));

        assertFalse(retry.sent());
        assertEquals("SKIPPED", retry.status());
        assertEquals(1, sendAttempts[0]);
        assertEquals(1, sent.size());
    }

    @Test
    void calculaPeriodoQuandoInputNaoInformaPeriodo() {
        List<WeeklyReport> sent = new ArrayList<>();
        GenerateWeeklyReportUseCase useCase = newUseCase(List.of(), sent, new InMemoryIdempotencyGateway());

        WeeklyReportResult result = useCase.execute(new WeeklyReportRequest(null));

        assertEquals("2026-W26", result.periodo());
        assertEquals("2026-W26", sent.getFirst().periodo());
    }

    private GenerateWeeklyReportUseCase newUseCase(
            List<WeeklyFeedback> feedbacks,
            List<WeeklyReport> sent,
            WeeklyReportIdempotencyGateway idempotencyGateway) {
        return new GenerateWeeklyReportUseCase(periodo -> feedbacks, sent::add, idempotencyGateway, CLOCK);
    }

    private WeeklyFeedback feedback(String dataEnvio, int nota, Urgencia urgencia) {
        return new WeeklyFeedback(UUID.randomUUID(), "Descricao valida", nota, urgencia, Instant.parse(dataEnvio));
    }

    private static class InMemoryIdempotencyGateway implements WeeklyReportIdempotencyGateway {
        private final Map<String, String> statuses = new HashMap<>();

        @Override
        public boolean tryStart(String periodo) {
            String status = statuses.get(periodo);
            if (status == null || "FAILED_BEFORE_SEND".equals(status)) {
                statuses.put(periodo, "PROCESSING");
                return true;
            }
            return false;
        }

        @Override
        public void markSent(String periodo) {
            statuses.put(periodo, "SENT");
        }

        @Override
        public void markFailedBeforeSend(String periodo, String reason) {
            statuses.put(periodo, "FAILED_BEFORE_SEND");
        }

        @Override
        public void markFailedAfterSendAttempt(String periodo, String reason) {
            statuses.put(periodo, "FAILED_AFTER_SEND_ATTEMPT");
        }
    }
}
