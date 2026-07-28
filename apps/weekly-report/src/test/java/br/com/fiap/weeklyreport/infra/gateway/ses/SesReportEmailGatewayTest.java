package br.com.fiap.weeklyreport.infra.gateway.ses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import br.com.fiap.weeklyreport.core.domain.WeeklyFeedback;
import br.com.fiap.weeklyreport.core.domain.WeeklyReport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

@ExtendWith(MockitoExtension.class)
class SesReportEmailGatewayTest {
    @Mock
    SesClient sesClient;

    private SesReportEmailGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new SesReportEmailGateway(sesClient, "admin@example.com", "no-reply@example.com");
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("message-1").build());
    }

    @Test
    void composesCompleteUtf8Report() {
        WeeklyFeedback critical = feedback("11111111-1111-1111-1111-111111111111", "Falha critica no atendimento", 2,
                Urgencia.CRITICA, "2026-06-22T10:00:00Z");
        WeeklyFeedback low = feedback("22222222-2222-2222-2222-222222222222", "Atendimento excelente", 9,
                Urgencia.BAIXA, "2026-06-23T10:00:00Z");
        WeeklyReport report = new WeeklyReport(
                "2026-W26",
                5.5,
                Map.of(LocalDate.parse("2026-06-22"), 1L, LocalDate.parse("2026-06-23"), 1L),
                Map.of(Urgencia.CRITICA, 1L, Urgencia.MEDIA, 0L, Urgencia.BAIXA, 1L),
                List.of(critical, low),
                List.of(critical));

        gateway.sendWeeklyReport(report);

        SendEmailRequest request = captureRequest();
        assertEquals("no-reply@example.com", request.source());
        assertEquals(List.of("admin@example.com"), request.destination().toAddresses());
        assertEquals("UTF-8", request.message().subject().charset());
        assertEquals("Relatorio semanal de feedbacks - 2026-W26", request.message().subject().data());
        assertEquals("UTF-8", request.message().body().text().charset());
        String body = request.message().body().text().data();
        assertTrue(body.contains("Media semanal: 5.50"));
        assertTrue(body.contains("- 2026-06-22: 1"));
        assertTrue(body.contains("- CRITICA: 1"));
        assertTrue(body.contains("- MEDIA: 0"));
        assertTrue(body.contains("- BAIXA: 1"));
        assertTrue(body.contains("Atendimento excelente"));
        assertTrue(body.contains("Feedbacks criticos\n- 2026-06-22T10:00:00Z | CRITICA | Falha critica no atendimento"));
    }

    @Test
    void composesEmptyWeekText() {
        gateway.sendWeeklyReport(new WeeklyReport(
                "2026-W27",
                0.0,
                Map.of(LocalDate.parse("2026-06-29"), 0L),
                Map.of(Urgencia.CRITICA, 0L, Urgencia.MEDIA, 0L, Urgencia.BAIXA, 0L),
                List.of(),
                List.of()));

        String body = captureRequest().message().body().text().data();
        assertTrue(body.contains("Media semanal: 0.00"));
        assertTrue(body.contains("Nenhum feedback encontrado na semana."));
        assertTrue(body.contains("Nenhum feedback critico encontrado na semana."));
    }

    private SendEmailRequest captureRequest() {
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        return captor.getValue();
    }

    private WeeklyFeedback feedback(String id, String description, int score, Urgencia urgency, String sentAt) {
        return new WeeklyFeedback(UUID.fromString(id), description, score, urgency, Instant.parse(sentAt));
    }
}
