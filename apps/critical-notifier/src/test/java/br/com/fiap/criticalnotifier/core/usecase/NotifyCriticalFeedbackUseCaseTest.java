package br.com.fiap.criticalnotifier.core.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmail;
import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmailComposer;
import br.com.fiap.criticalnotifier.core.domain.ProcessingLease;
import br.com.fiap.criticalnotifier.core.exception.EmailSendAmbiguousException;
import br.com.fiap.criticalnotifier.core.exception.EmailSendRetryableException;
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
import java.util.Optional;
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
    void permiteRetryQuandoSesFalhaAntesDaAceitacao() {
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        RetryableFailingEmailGateway failingGateway = new RetryableFailingEmailGateway();
        NotifyCriticalFeedbackUseCase useCase = newUseCase(idempotencyGateway, failingGateway);
        CriticalFeedbackEvent event = sampleEvent();

        assertThrows(EmailSendRetryableException.class, () -> useCase.execute(event));
        assertEquals("FAILED_BEFORE_SEND", idempotencyGateway.statusByFeedbackId.get(FEEDBACK_ID));
        assertEquals(1, failingGateway.attempts);

        RecordingEmailGateway successGateway = new RecordingEmailGateway();
        NotifyCriticalFeedbackUseCase retryUseCase = newUseCase(idempotencyGateway, successGateway);
        NotificationResult result = retryUseCase.execute(event);

        assertEquals(NotificationResult.SENT, result);
        assertEquals(1, successGateway.sentEmails.size());
    }

    @Test
    void retomaProcessamentoQuandoLeaseProcessingExpira() {
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        idempotencyGateway.statusByFeedbackId.put(FEEDBACK_ID, "PROCESSING");
        idempotencyGateway.ownerTokenByFeedbackId.put(FEEDBACK_ID, "owner-expired");
        idempotencyGateway.leaseExpiresAtByFeedbackId.put(FEEDBACK_ID, Instant.parse("2026-05-31T12:00:00Z"));

        RecordingEmailGateway emailGateway = new RecordingEmailGateway();
        NotifyCriticalFeedbackUseCase useCase = newUseCase(idempotencyGateway, emailGateway);

        NotificationResult result = useCase.execute(sampleEvent());

        assertEquals(NotificationResult.SENT, result);
        assertEquals(1, emailGateway.sentEmails.size());
        assertEquals("SENT", idempotencyGateway.statusByFeedbackId.get(FEEDBACK_ID));
    }

    @Test
    void bloqueiaRetryQuandoEstadoSendAttemptedPersiste() {
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        idempotencyGateway.statusByFeedbackId.put(FEEDBACK_ID, "SEND_ATTEMPTED");
        idempotencyGateway.ownerTokenByFeedbackId.put(FEEDBACK_ID, "owner-1");

        RecordingEmailGateway emailGateway = new RecordingEmailGateway();
        NotifyCriticalFeedbackUseCase useCase = newUseCase(idempotencyGateway, emailGateway);

        NotificationResult result = useCase.execute(sampleEvent());

        assertEquals(NotificationResult.SKIPPED, result);
        assertEquals(0, emailGateway.sentEmails.size());
    }

    @Test
    void bloqueiaRetryQuandoFalhaDepoisDaTentativaDeEnvio() {
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        AmbiguousFailingEmailGateway failingGateway = new AmbiguousFailingEmailGateway();
        NotifyCriticalFeedbackUseCase useCase = newUseCase(idempotencyGateway, failingGateway);
        CriticalFeedbackEvent event = sampleEvent();

        assertThrows(EmailSendAmbiguousException.class, () -> useCase.execute(event));
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
        private final Map<UUID, String> ownerTokenByFeedbackId = new HashMap<>();
        private final Map<UUID, Instant> leaseExpiresAtByFeedbackId = new HashMap<>();
        private Instant now = Instant.parse("2026-05-31T13:00:00Z");

        @Override
        public Optional<ProcessingLease> tryStart(UUID feedbackId) {
            String status = statusByFeedbackId.get(feedbackId);
            if (status == null || "FAILED_BEFORE_SEND".equals(status)) {
                return acquireLease(feedbackId);
            }
            if ("PROCESSING".equals(status)) {
                Instant leaseExpiresAt = leaseExpiresAtByFeedbackId.get(feedbackId);
                if (leaseExpiresAt != null && leaseExpiresAt.isBefore(now)) {
                    return acquireLease(feedbackId);
                }
            }
            return Optional.empty();
        }

        @Override
        public void markAboutToSend(UUID feedbackId, ProcessingLease lease) {
            assertOwner(feedbackId, lease);
            statusByFeedbackId.put(feedbackId, "SEND_ATTEMPTED");
        }

        @Override
        public void markSent(UUID feedbackId, ProcessingLease lease) {
            assertOwner(feedbackId, lease);
            statusByFeedbackId.put(feedbackId, "SENT");
        }

        @Override
        public void markFailedBeforeSend(UUID feedbackId, ProcessingLease lease, String reason) {
            assertOwner(feedbackId, lease);
            statusByFeedbackId.put(feedbackId, "FAILED_BEFORE_SEND");
        }

        @Override
        public void markFailedAfterSendAttempt(UUID feedbackId, ProcessingLease lease, String reason) {
            assertOwner(feedbackId, lease);
            statusByFeedbackId.put(feedbackId, "FAILED_AFTER_SEND_ATTEMPT");
        }

        private Optional<ProcessingLease> acquireLease(UUID feedbackId) {
            ProcessingLease lease = new ProcessingLease(UUID.randomUUID().toString());
            statusByFeedbackId.put(feedbackId, "PROCESSING");
            ownerTokenByFeedbackId.put(feedbackId, lease.ownerToken());
            leaseExpiresAtByFeedbackId.put(feedbackId, now.plusSeconds(60));
            return Optional.of(lease);
        }

        private void assertOwner(UUID feedbackId, ProcessingLease lease) {
            String ownerToken = ownerTokenByFeedbackId.get(feedbackId);
            if (!lease.ownerToken().equals(ownerToken)) {
                throw new IllegalStateException("Stale owner token.");
            }
        }
    }

    private static class RecordingEmailGateway implements EmailGateway {
        private final List<CriticalNotificationEmail> sentEmails = new ArrayList<>();

        @Override
        public void sendCriticalNotification(CriticalNotificationEmail email) {
            sentEmails.add(email);
        }
    }

    private static class RetryableFailingEmailGateway implements EmailGateway {
        private int attempts;

        @Override
        public void sendCriticalNotification(CriticalNotificationEmail email) {
            attempts++;
            throw new EmailSendRetryableException("SES unavailable", new IllegalStateException("SES unavailable"));
        }
    }

    private static class AmbiguousFailingEmailGateway implements EmailGateway {
        @Override
        public void sendCriticalNotification(CriticalNotificationEmail email) {
            throw new EmailSendAmbiguousException("SES timeout", new IllegalStateException("SES timeout"));
        }
    }

    private static class FailingCriticalNotificationEmailComposer extends CriticalNotificationEmailComposer {
        @Override
        public CriticalNotificationEmail compose(CriticalFeedbackEvent event) {
            throw new IllegalStateException("Unable to compose e-mail");
        }
    }
}
