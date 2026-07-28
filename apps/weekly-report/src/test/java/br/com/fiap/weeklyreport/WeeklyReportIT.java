package br.com.fiap.weeklyreport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.fiap.weeklyreport.core.usecase.GenerateWeeklyReportUseCase;
import br.com.fiap.weeklyreport.infra.gateway.dynamodb.DynamoDbWeeklyFeedbackReader;
import br.com.fiap.weeklyreport.infra.gateway.dynamodb.DynamoDbWeeklyReportIdempotencyGateway;
import br.com.fiap.weeklyreport.infra.gateway.ses.SesReportEmailGateway;
import br.com.fiap.weeklyreport.infra.lambda.WeeklyReportHandler;
import br.com.fiap.weeklyreport.support.WeeklyReportFakecloudFixture;
import br.com.fiap.weeklyreport.support.WeeklyReportFakecloudFixture.RecordedEmail;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class WeeklyReportIT {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-24T10:00:00Z"), ZoneOffset.UTC);

    private WeeklyReportFakecloudFixture fixture;
    private WeeklyReportHandler handler;

    @BeforeEach
    void setUp() {
        fixture = new WeeklyReportFakecloudFixture();
        handler = new WeeklyReportHandler(new GenerateWeeklyReportUseCase(
                new DynamoDbWeeklyFeedbackReader(fixture.dynamoDb(), fixture.feedbackTable()),
                new SesReportEmailGateway(fixture.ses(), fixture.recipient(), fixture.sender()),
                new DynamoDbWeeklyReportIdempotencyGateway(fixture.dynamoDb(), fixture.processingTable(), CLOCK),
                CLOCK));
    }

    @AfterEach
    void tearDown() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void populatedWeekIncludesOnlyRequestedPeriodThroughHandler() {
        fixture.putFeedback("2026-W26", "Falha critica no suporte", 2, "CRITICA", "2026-06-22T10:00:00Z");
        fixture.putFeedback("2026-W26", "Atendimento razoavel", 5, "MEDIA", "2026-06-23T10:00:00Z");
        fixture.putFeedback("2026-W25", "Nao pertence ao relatorio", 10, "BAIXA", "2026-06-15T10:00:00Z");

        WeeklyReportHandler.Output output = handler.handleRequest(new WeeklyReportHandler.Input("2026-W26"), null);

        assertEquals("SENT", output.status());
        assertTrue(output.sent());
        assertEquals("SENT", processing("2026-W26").get("status").s());
        List<RecordedEmail> emails = fixture.emails();
        assertEquals(1, emails.size());
        RecordedEmail email = emails.getFirst();
        assertEquals(fixture.sender(), email.from());
        assertEquals(List.of(fixture.recipient()), email.to());
        assertEquals("Relatorio semanal de feedbacks - 2026-W26", email.subject());
        assertTrue(email.textBody().contains("Media semanal: 3.50"));
        assertTrue(email.textBody().contains("- CRITICA: 1"));
        assertTrue(email.textBody().contains("- MEDIA: 1"));
        assertTrue(email.textBody().contains("Falha critica no suporte"));
        assertTrue(email.textBody().contains("Feedbacks criticos"));
        assertFalse(email.textBody().contains("Nao pertence ao relatorio"));
    }

    @Test
    void emptyWeekSendsZeroReportAndPersistsSentState() {
        WeeklyReportHandler.Output output = handler.handleRequest(new WeeklyReportHandler.Input("2026-W27"), null);

        assertEquals("SENT", output.status());
        assertTrue(output.sent());
        assertEquals("SENT", processing("2026-W27").get("status").s());
        List<RecordedEmail> emails = fixture.emails();
        assertEquals(1, emails.size());
        String body = emails.getFirst().textBody();
        assertTrue(body.contains("Media semanal: 0.00"));
        assertTrue(body.contains("- CRITICA: 0"));
        assertTrue(body.contains("- MEDIA: 0"));
        assertTrue(body.contains("- BAIXA: 0"));
        assertTrue(body.contains("Nenhum feedback encontrado na semana."));
    }

    @Test
    void duplicatePeriodIsSkippedWithoutSecondEmail() {
        fixture.putFeedback("2026-W26", "Falha critica no suporte", 2, "CRITICA", "2026-06-22T10:00:00Z");

        WeeklyReportHandler.Output first = handler.handleRequest(new WeeklyReportHandler.Input("2026-W26"), null);
        WeeklyReportHandler.Output second = handler.handleRequest(new WeeklyReportHandler.Input("2026-W26"), null);

        assertEquals("SENT", first.status());
        assertEquals("SKIPPED", second.status());
        assertFalse(second.sent());
        assertEquals("SENT", processing("2026-W26").get("status").s());
        assertEquals(1, fixture.emails().size());
    }

    @Test
    void differentPeriodsHaveIsolatedIdempotencyKeys() {
        fixture.putFeedback("2026-W26", "Feedback da semana vinte e seis", 8, "BAIXA", "2026-06-22T10:00:00Z");
        fixture.putFeedback("2026-W27", "Feedback da semana vinte e sete", 5, "MEDIA", "2026-06-29T10:00:00Z");

        WeeklyReportHandler.Output week26 = handler.handleRequest(new WeeklyReportHandler.Input("2026-W26"), null);
        WeeklyReportHandler.Output week27 = handler.handleRequest(new WeeklyReportHandler.Input("2026-W27"), null);

        assertEquals("SENT", week26.status());
        assertEquals("SENT", week27.status());
        assertEquals("SENT", processing("2026-W26").get("status").s());
        assertEquals("SENT", processing("2026-W27").get("status").s());
        assertEquals(2, fixture.emails().size());
        assertTrue(fixture.emails().stream().anyMatch(email -> email.subject().endsWith("2026-W26")));
        assertTrue(fixture.emails().stream().anyMatch(email -> email.subject().endsWith("2026-W27")));
    }

    private Map<String, AttributeValue> processing(String period) {
        return fixture.processingRecord(period);
    }
}
