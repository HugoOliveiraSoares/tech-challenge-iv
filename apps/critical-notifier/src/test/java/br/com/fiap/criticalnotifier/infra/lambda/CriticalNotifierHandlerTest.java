package br.com.fiap.criticalnotifier.infra.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.criticalnotifier.core.usecase.NotifyCriticalFeedbackUseCase;
import br.com.fiap.criticalnotifier.core.usecase.NotifyCriticalFeedbackUseCase.NotificationResult;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNS;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CriticalNotifierHandlerTest {
    private static final UUID FEEDBACK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    NotifyCriticalFeedbackUseCase notifyCriticalFeedbackUseCase;

    private CriticalNotifierHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        handler = new CriticalNotifierHandler(
                new SnsCriticalFeedbackEventParser(objectMapper), notifyCriticalFeedbackUseCase);
    }

    @Test
    void processaEventoSnsValido() throws Exception {
        String message = objectMapper.writeValueAsString(new EventPayload(
                FEEDBACK_ID.toString(),
                "correlation-1",
                "A aula estava confusa e nao consegui acompanhar o conteudo.",
                2,
                Urgencia.CRITICA,
                Instant.parse("2026-05-31T13:00:00Z")));

        when(notifyCriticalFeedbackUseCase.execute(any(CriticalFeedbackEvent.class)))
                .thenReturn(NotificationResult.SENT);

        CriticalNotifierHandler.Output output = handler.handleRequest(snsEvent(message), null);

        assertEquals("OK", output.status());
        assertEquals(1, output.sent());
        assertEquals(0, output.skipped());
        ArgumentCaptor<CriticalFeedbackEvent> eventCaptor = ArgumentCaptor.forClass(CriticalFeedbackEvent.class);
        verify(notifyCriticalFeedbackUseCase).execute(eventCaptor.capture());
        CriticalFeedbackEvent event = eventCaptor.getValue();
        assertEquals(FEEDBACK_ID, event.feedbackId());
        assertEquals("correlation-1", event.correlationId());
        assertEquals("A aula estava confusa e nao consegui acompanhar o conteudo.", event.descricao());
        assertEquals(2, event.nota());
        assertEquals(Urgencia.CRITICA, event.urgencia());
        assertEquals(Instant.parse("2026-05-31T13:00:00Z"), event.dataEnvio());
    }

    @Test
    void contaNotificacoesIgnoradasPorIdempotencia() throws Exception {
        String message = objectMapper.writeValueAsString(new EventPayload(
                FEEDBACK_ID.toString(),
                "correlation-1",
                "A aula estava confusa e nao consegui acompanhar o conteudo.",
                2,
                Urgencia.CRITICA,
                Instant.parse("2026-05-31T13:00:00Z")));

        when(notifyCriticalFeedbackUseCase.execute(any(CriticalFeedbackEvent.class)))
                .thenReturn(NotificationResult.SKIPPED);

        CriticalNotifierHandler.Output output = handler.handleRequest(snsEvent(message), null);

        assertEquals(0, output.sent());
        assertEquals(1, output.skipped());
    }

    @Test
    void rejeitaEventoSnsSemRegistros() {
        assertThrows(DomainValidationException.class, () -> handler.handleRequest(new SNSEvent(), null));
    }

    @Test
    void agregaResultadosDeMultiplosRegistros() throws Exception {
        String firstMessage = message(FEEDBACK_ID, "correlation-1");
        String secondMessage = message(
                UUID.fromString("22222222-2222-2222-2222-222222222222"), "correlation-2");
        when(notifyCriticalFeedbackUseCase.execute(any(CriticalFeedbackEvent.class)))
                .thenReturn(NotificationResult.SENT, NotificationResult.SKIPPED);

        SNSEvent event = new SNSEvent();
        event.setRecords(List.of(snsRecord(firstMessage), snsRecord(secondMessage)));

        CriticalNotifierHandler.Output output = handler.handleRequest(event, null);

        assertEquals("OK", output.status());
        assertEquals(1, output.sent());
        assertEquals(1, output.skipped());
    }

    @Test
    void rejeitaRegistroSnsMalformado() {
        assertThrows(DomainValidationException.class, () -> handler.handleRequest(snsEvent("{"), null));
    }

    @Test
    void rejeitaEventoSnsNulo() {
        assertThrows(DomainValidationException.class, () -> handler.handleRequest(null, null));
    }

    @Test
    void rejeitaRegistroSnsComEnvelopeNulo() {
        SNSRecord record = new SNSRecord();
        record.setSns(null);
        SNSEvent event = new SNSEvent();
        event.setRecords(List.of(record));
        assertThrows(DomainValidationException.class, () -> handler.handleRequest(event, null));
    }

    @Test
    void rejeitaRegistroSnsComMensagemNula() {
        SNS sns = new SNS();
        sns.setMessage(null);
        SNSRecord record = new SNSRecord();
        record.setSns(sns);
        SNSEvent event = new SNSEvent();
        event.setRecords(List.of(record));
        assertThrows(DomainValidationException.class, () -> handler.handleRequest(event, null));
    }

    @Test
    void contaTodosRegistrosIgnorados() throws Exception {
        String firstMessage = message(FEEDBACK_ID, "correlation-1");
        String secondMessage = message(
                UUID.fromString("22222222-2222-2222-2222-222222222222"), "correlation-2");
        when(notifyCriticalFeedbackUseCase.execute(any(CriticalFeedbackEvent.class)))
                .thenReturn(NotificationResult.SKIPPED, NotificationResult.SKIPPED);

        SNSEvent event = new SNSEvent();
        event.setRecords(List.of(snsRecord(firstMessage), snsRecord(secondMessage)));

        CriticalNotifierHandler.Output output = handler.handleRequest(event, null);

        assertEquals("OK", output.status());
        assertEquals(0, output.sent());
        assertEquals(2, output.skipped());
    }

    @Test
    void preservaContagemQuandoExcecaoOcorreNoMeioDoLote() throws Exception {
        String firstMessage = message(FEEDBACK_ID, "correlation-1");
        String secondMessage = message(
                UUID.fromString("22222222-2222-2222-2222-222222222222"), "correlation-2");
        when(notifyCriticalFeedbackUseCase.execute(any(CriticalFeedbackEvent.class)))
                .thenReturn(NotificationResult.SENT)
                .thenThrow(new RuntimeException("Unexpected failure"));

        SNSEvent event = new SNSEvent();
        event.setRecords(List.of(snsRecord(firstMessage), snsRecord(secondMessage)));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> handler.handleRequest(event, null));
        assertEquals("Unexpected failure", exception.getMessage());
    }

    private SNSEvent snsEvent(String message) {
        SNSEvent event = new SNSEvent();
        event.setRecords(List.of(snsRecord(message)));
        return event;
    }

    private SNSRecord snsRecord(String message) {
        SNS sns = new SNS();
        sns.setMessage(message);

        SNSRecord record = new SNSRecord();
        record.setSns(sns);
        return record;
    }

    private String message(UUID feedbackId, String correlationId) throws Exception {
        return objectMapper.writeValueAsString(new EventPayload(
                feedbackId.toString(),
                correlationId,
                "A aula estava confusa e nao consegui acompanhar o conteudo.",
                2,
                Urgencia.CRITICA,
                Instant.parse("2026-05-31T13:00:00Z")));
    }

    private record EventPayload(
            String feedbackId,
            String correlationId,
            String descricao,
            int nota,
            Urgencia urgencia,
            Instant dataEnvio) {
    }
}
