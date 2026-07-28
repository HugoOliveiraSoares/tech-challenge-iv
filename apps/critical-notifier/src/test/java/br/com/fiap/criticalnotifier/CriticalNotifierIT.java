package br.com.fiap.criticalnotifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmailComposer;
import br.com.fiap.criticalnotifier.core.usecase.NotifyCriticalFeedbackUseCase;
import br.com.fiap.criticalnotifier.infra.gateway.dynamodb.DynamoDbCriticalNotificationIdempotencyGateway;
import br.com.fiap.criticalnotifier.infra.gateway.ses.SesCriticalEmailGateway;
import br.com.fiap.criticalnotifier.infra.lambda.CriticalNotifierHandler;
import br.com.fiap.criticalnotifier.infra.lambda.SnsCriticalFeedbackEventParser;
import br.com.fiap.criticalnotifier.support.CriticalNotifierFakecloudFixture;
import br.com.fiap.criticalnotifier.support.CriticalNotifierFakecloudFixture.FakeEmail;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNS;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CriticalNotifierIT {
    private static final Instant NOW = Instant.parse("2026-05-31T13:00:00Z");
    private static final UUID FEEDBACK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static CriticalNotifierFakecloudFixture fixture;
    private static CriticalNotifierHandler handler;

    @BeforeAll
    static void setUp() {
        fixture = new CriticalNotifierFakecloudFixture();
        var idempotencyGateway = new DynamoDbCriticalNotificationIdempotencyGateway(
                fixture.dynamoDbClient(), fixture.tableName(), Clock.fixed(NOW, ZoneOffset.UTC), 60);
        var useCase = new NotifyCriticalFeedbackUseCase(
                idempotencyGateway,
                new CriticalNotificationEmailComposer(),
                new SesCriticalEmailGateway(fixture.sesClient(), fixture.recipient(), fixture.sender()));
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        handler = new CriticalNotifierHandler(new SnsCriticalFeedbackEventParser(objectMapper), useCase);
    }

    @AfterAll
    static void tearDown() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void entregaEventoCriticoUmaVezPelaFronteiraDaAplicacao() {
        SNSEvent event = snsEvent("""
                {
                  "feedbackId": "11111111-1111-1111-1111-111111111111",
                  "correlationId": "correlation-it-1",
                  "descricao": "A aula estava confusa e nao consegui acompanhar o conteudo.",
                  "nota": 2,
                  "urgencia": "CRITICA",
                  "dataEnvio": "2026-05-31T13:00:00Z"
                }
                """);

        CriticalNotifierHandler.Output first = handler.handleRequest(event, null);

        assertEquals(1, first.sent());
        assertEquals(0, first.skipped());
        assertEquals("SENT", fixture.state(FEEDBACK_ID).get("status").s());
        assertTrue(fixture.state(FEEDBACK_ID).containsKey("sentAt"));
        List<FakeEmail> emails = fixture.emailsToRecipient();
        assertEquals(1, emails.size());
        FakeEmail email = emails.getFirst();
        assertEquals(fixture.sender(), email.from());
        assertEquals(fixture.recipient(), email.to());
        assertTrue(email.subject().contains("ALERTA: Feedback critico recebido"));
        assertTrue(email.subject().contains(FEEDBACK_ID.toString()));
        assertTrue(email.textBody().contains("A aula estava confusa e nao consegui acompanhar o conteudo."));
        assertTrue(email.textBody().contains("Urgencia: CRITICA"));
        assertTrue(email.textBody().contains("- nota: 2"));
        assertTrue(email.textBody().contains("correlation-it-1"));

        CriticalNotifierHandler.Output duplicate = handler.handleRequest(event, null);

        assertEquals(0, duplicate.sent());
        assertEquals(1, duplicate.skipped());
        assertEquals("SENT", fixture.state(FEEDBACK_ID).get("status").s());
        assertEquals(1, fixture.emailsToRecipient().size());
    }

    private static SNSEvent snsEvent(String message) {
        SNS sns = new SNS();
        sns.setMessage(message);
        SNSRecord record = new SNSRecord();
        record.setSns(sns);
        SNSEvent event = new SNSEvent();
        event.setRecords(List.of(record));
        return event;
    }
}
