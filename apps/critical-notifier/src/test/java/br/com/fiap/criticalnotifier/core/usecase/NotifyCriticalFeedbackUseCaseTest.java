package br.com.fiap.criticalnotifier.core.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmail;
import br.com.fiap.criticalnotifier.core.domain.CriticalNotificationEmailComposer;
import br.com.fiap.criticalnotifier.core.domain.ProcessingLease;
import br.com.fiap.criticalnotifier.core.exception.EmailSendAmbiguousException;
import br.com.fiap.criticalnotifier.core.exception.EmailSendRetryableException;
import br.com.fiap.criticalnotifier.core.gateway.EmailGateway;
import br.com.fiap.criticalnotifier.core.gateway.NotificationIdempotencyGateway;
import br.com.fiap.criticalnotifier.core.usecase.NotifyCriticalFeedbackUseCase.NotificationResult;
import br.com.fiap.criticalnotifier.infra.gateway.ses.SesCriticalEmailGateway;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

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
    void naoCompoeEmailQuandoIdempotenciaBloqueiaEvento() {
        NotificationIdempotencyGateway idempotencyGateway = mock(NotificationIdempotencyGateway.class);
        CriticalNotificationEmailComposer emailComposer = mock(CriticalNotificationEmailComposer.class);
        EmailGateway emailGateway = mock(EmailGateway.class);
        when(idempotencyGateway.tryStart(FEEDBACK_ID)).thenReturn(Optional.empty());
        NotifyCriticalFeedbackUseCase useCase =
                new NotifyCriticalFeedbackUseCase(idempotencyGateway, emailComposer, emailGateway);

        NotificationResult result = useCase.execute(sampleEvent());

        assertEquals(NotificationResult.SKIPPED, result);
        verify(idempotencyGateway).tryStart(FEEDBACK_ID);
        verifyNoInteractions(emailComposer, emailGateway);
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
    void permiteRetryQuandoThrottlingSesEsgotaTentativas() {
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
        SesClient sesClient = mock(SesClient.class);
        doThrow(throttlingException()).when(sesClient).sendEmail(any(SendEmailRequest.class));
        EmailGateway failingGateway = new SesCriticalEmailGateway(
                sesClient, "admin@example.com", "no-reply@example.com");
        NotifyCriticalFeedbackUseCase useCase = newUseCase(idempotencyGateway, failingGateway);
        CriticalFeedbackEvent event = sampleEvent();

        assertThrows(EmailSendRetryableException.class, () -> useCase.execute(event));
        assertEquals("FAILED_BEFORE_SEND", idempotencyGateway.statusByFeedbackId.get(FEEDBACK_ID));

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
    void bloqueiaRetryQuandoSesRetornaHttp5xx() {
        SesClient sesClient = mock(SesClient.class);
        doThrow(SesException.builder().statusCode(503).message("Service unavailable").build())
                .when(sesClient)
                .sendEmail(any(SendEmailRequest.class));

        assertFalhaAmbiguaBloqueiaRetry(new SesCriticalEmailGateway(
                sesClient, "admin@example.com", "no-reply@example.com"));
    }

    @Test
    void bloqueiaRetryQuandoSesTemFalhaDeTransporte() {
        SesClient sesClient = mock(SesClient.class);
        doThrow(SdkClientException.builder()
                        .message("Request timed out")
                        .cause(new SocketTimeoutException("Read timed out"))
                        .build())
                .when(sesClient)
                .sendEmail(any(SendEmailRequest.class));

        assertFalhaAmbiguaBloqueiaRetry(new SesCriticalEmailGateway(
                sesClient, "admin@example.com", "no-reply@example.com"));
    }

    private void assertFalhaAmbiguaBloqueiaRetry(EmailGateway failingGateway) {
        InMemoryIdempotencyGateway idempotencyGateway = new InMemoryIdempotencyGateway();
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

    private SesException throttlingException() {
        return (SesException) SesException.builder()
                .statusCode(400)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .serviceName("SES")
                        .errorCode("Throttling")
                        .errorMessage("Maximum sending rate exceeded")
                        .build())
                .build();
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
                "FeedbackCritico",
                "1.0",
                FEEDBACK_ID,
                "A aula estava confusa e nao consegui acompanhar o conteudo.",
                2,
                Urgencia.CRITICA,
                DATA_ENVIO,
                "2026-W22",
                "correlation-1");
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

    private static class FailingCriticalNotificationEmailComposer extends CriticalNotificationEmailComposer {
        @Override
        public CriticalNotificationEmail compose(CriticalFeedbackEvent event) {
            throw new IllegalStateException("Unable to compose e-mail");
        }
    }
}
