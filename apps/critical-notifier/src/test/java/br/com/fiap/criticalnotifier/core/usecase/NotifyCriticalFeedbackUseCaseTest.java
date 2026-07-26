package br.com.fiap.criticalnotifier.core.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmail;
import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmailComposer;
import br.com.fiap.criticalnotifier.core.gateway.EmailGateway;
import br.com.fiap.criticalnotifier.core.gateway.NotificationIdempotencyGateway;
import br.com.fiap.criticalnotifier.core.usecase.NotifyCriticalFeedbackUseCase.NotificationResult;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotifyCriticalFeedbackUseCaseTest {
    private static final UUID FEEDBACK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant DATA_ENVIO = Instant.parse("2026-05-31T13:00:00Z");

    @Test
    void enviaEmailQuandoIdempotenciaPermite() {
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        RecordingEmailGateway emailGateway = new RecordingEmailGateway();
        NotifyCriticalFeedbackUseCase useCase = newUseCase(idempotencyGateway, emailGateway);
        CriticalFeedbackEvent event = sampleEvent();

        NotificationResult result = useCase.execute(event);

        assertEquals(NotificationResult.SENT, result);
        assertEquals(1, emailGateway.sentEmails.size());
        assertEquals("SENT", idempotencyGateway.statusByFeedbackId.get(FEEDBACK_ID));
    }

    @Test
    void naoEnviaEmailDuplicadoParaMesmoFeedbackId() {
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        RecordingEmailGateway emailGateway = new RecordingEmailGateway();
        NotifyCriticalFeedbackUseCase useCase = newUseCase(idempotencyGateway, emailGateway);
        CriticalFeedbackEvent event = sampleEvent();

        useCase.execute(event);
        NotificationResult secondAttempt = useCase.execute(event);

        assertEquals(NotificationResult.SKIPPED, secondAttempt);
        assertEquals(1, emailGateway.sentEmails.size());
    }

    @Test
    void permiteRetryQuandoFalhaAntesDoEnvio() {
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        RecordingEmailGateway emailGateway = new RecordingEmailGateway();
        NotifyCriticalFeedbackUseCase useCase = new NotifyCriticalFeedbackUseCase(
                idempotencyGateway,
                new FailingCriticalNotificationEmailComposer(),
                emailGateway);
        CriticalFeedbackEvent event = sampleEvent();

        assertThrows(IllegalStateException.class, () -> useCase.execute(event));
        assertEquals("FAILED_BEFORE_SEND", idempotencyGateway.statusByFeedbackId.get(FEEDBACK_ID));
        assertEquals(0, emailGateway.sentEmails.size());

        NotifyCriticalFeedbackUseCase retryUseCase = newUseCase(idempotencyGateway, emailGateway);
        NotificationResult result = retryUseCase.execute(event);

        assertEquals(NotificationResult.SENT, result);
        assertEquals(1, emailGateway.sentEmails.size());
    }

    @Test
    void bloqueiaRetryQuandoFalhaDepoisDaTentativaDeEnvio() {
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        FailingEmailGateway failingGateway = new FailingEmailGateway();
        NotifyCriticalFeedbackUseCase useCase = newUseCase(idempotencyGateway, failingGateway);
        CriticalFeedbackEvent event = sampleEvent();

        assertThrows(IllegalStateException.class, () -> useCase.execute(event));
        assertEquals("FAILED_AFTER_SEND_ATTEMPT", idempotencyGateway.statusByFeedbackId.get(FEEDBACK_ID));

        RecordingEmailGateway successGateway = new RecordingEmailGateway();
        NotifyCriticalFeedbackUseCase retryUseCase = newUseCase(idempotencyGateway, successGateway);
        NotificationResult result = retryUseCase.execute(event);

        assertEquals(NotificationResult.SKIPPED, result);
        assertEquals(0, successGateway.sentEmails.size());
    }

    private NotifyCriticalFeedbackUseCase newUseCase(
            NotificationIdempotencyGateway idempotencyGateway,
            EmailGateway emailGateway) {
        return new NotifyCriticalFeedbackUseCase(
                idempotencyGateway,
                new CriticalNotificationEmailComposer(),
                emailGateway);
    }

    private CriticalFeedbackEvent sampleEvent() {
        return new CriticalFeedbackEvent(
                FEEDBACK_ID,
                "correlation-1",
                "A aula estava confusa e nao consegui acompanhar o conteudo.",
                2,
                Urgencia.CRITICA,
                DATA_ENVIO);
    }

    private static class InMemoryIdempotencyGateway implements NotificationIdempotencyGateway {
        private final Map<UUID, String> statusByFeedbackId = new HashMap<>();

        @Override
        public boolean tryStart(UUID feedbackId) {
            String status = statusByFeedbackId.get(feedbackId);
            if (status == null || "FAILED_BEFORE_SEND".equals(status)) {
                statusByFeedbackId.put(feedbackId, "PROCESSING");
                return true;
            }
            return false;
        }

        @Override
        public void markSent(UUID feedbackId) {
            statusByFeedbackId.put(feedbackId, "SENT");
        }

        @Override
        public void markFailedBeforeSend(UUID feedbackId, String reason) {
            statusByFeedbackId.put(feedbackId, "FAILED_BEFORE_SEND");
        }

        @Override
        public void markFailedAfterSendAttempt(UUID feedbackId, String reason) {
            statusByFeedbackId.put(feedbackId, "FAILED_AFTER_SEND_ATTEMPT");
        }
    }

    private static class RecordingEmailGateway implements EmailGateway {
        private final List<CriticalNotificationEmail> sentEmails = new ArrayList<>();

        @Override
        public void sendCriticalNotification(CriticalNotificationEmail email) {
            sentEmails.add(email);
        }
    }

    private static class FailingEmailGateway implements EmailGateway {
        @Override
        public void sendCriticalNotification(CriticalNotificationEmail email) {
            throw new IllegalStateException("SES unavailable");
        }
    }

    private static class FailingCriticalNotificationEmailComposer extends CriticalNotificationEmailComposer {
        @Override
        public CriticalNotificationEmail compose(CriticalFeedbackEvent event) {
            throw new IllegalStateException("Unable to compose e-mail");
        }
    }
}
