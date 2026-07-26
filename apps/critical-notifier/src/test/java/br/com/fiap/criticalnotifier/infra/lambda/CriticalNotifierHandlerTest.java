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
        verify(notifyCriticalFeedbackUseCase).execute(any(CriticalFeedbackEvent.class));
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

    private SNSEvent snsEvent(String message) {
        SNS sns = new SNS();
        sns.setMessage(message);

        SNSRecord record = new SNSRecord();
        record.setSns(sns);

        SNSEvent event = new SNSEvent();
        event.setRecords(List.of(record));
        return event;
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
