package br.com.fiap.criticalnotifier.infra.gateway.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.fiap.criticalnotifier.core.domain.ProcessingLease;
import br.com.fiap.criticalnotifier.support.CriticalNotifierFakecloudFixture;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

class DynamoDbCriticalNotificationIdempotencyGatewayIT {
    private static final Instant NOW = Instant.parse("2026-05-31T13:00:00Z");

    private static CriticalNotifierFakecloudFixture fixture;
    private static DynamoDbCriticalNotificationIdempotencyGateway gateway;

    @BeforeAll
    static void setUp() {
        fixture = new CriticalNotifierFakecloudFixture();
        gateway = new DynamoDbCriticalNotificationIdempotencyGateway(
                fixture.dynamoDbClient(), fixture.tableName(), Clock.fixed(NOW, ZoneOffset.UTC), 60);
    }

    @AfterAll
    static void tearDown() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void leaseAtivoBloqueiaConcorrenteELeaseExpiradoPodeSerReadquirido() {
        UUID activeId = UUID.randomUUID();
        fixture.putState(activeId, "PROCESSING", "active-owner", NOW.plusSeconds(30).toString());
        assertTrue(gateway.tryStart(activeId).isEmpty());
        assertEquals("active-owner", fixture.state(activeId).get("ownerToken").s());

        UUID expiredId = UUID.randomUUID();
        fixture.putState(expiredId, "PROCESSING", "expired-owner", NOW.minusSeconds(1).toString());
        ProcessingLease newLease = gateway.tryStart(expiredId).orElseThrow();
        assertEquals("PROCESSING", fixture.state(expiredId).get("status").s());
        assertEquals(newLease.ownerToken(), fixture.state(expiredId).get("ownerToken").s());
    }

    @Test
    void failedBeforeSendPodeSerReadquirido() {
        UUID feedbackId = UUID.randomUUID();
        fixture.putState(feedbackId, "FAILED_BEFORE_SEND", "failed-owner", null);

        assertTrue(gateway.tryStart(feedbackId).isPresent());
        assertEquals("PROCESSING", fixture.state(feedbackId).get("status").s());
        assertFalse(fixture.state(feedbackId).containsKey("failureReason"));
    }

    @Test
    void estadosTerminaisEAmbiguosBloqueiamReadquisicao() {
        for (String status : List.of("SENT", "SEND_ATTEMPTED", "FAILED_AFTER_SEND_ATTEMPT")) {
            UUID feedbackId = UUID.randomUUID();
            fixture.putState(feedbackId, status, "owner-" + status, null);

            assertTrue(gateway.tryStart(feedbackId).isEmpty(), status);
            assertEquals(status, fixture.state(feedbackId).get("status").s());
        }
    }

    @Test
    void somenteUmaAquisicaoConcorrenteVence() throws Exception {
        UUID feedbackId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstAttempt = executor.submit(() -> {
                start.await();
                return gateway.tryStart(feedbackId);
            });
            var secondAttempt = executor.submit(() -> {
                start.await();
                return gateway.tryStart(feedbackId);
            });
            start.countDown();
            List<Optional<ProcessingLease>> results = List.of(firstAttempt.get(), secondAttempt.get());

            assertEquals(1, results.stream().filter(Optional::isPresent).count());
            ProcessingLease winner = results.stream().flatMap(Optional::stream).findFirst().orElseThrow();
            assertEquals(winner.ownerToken(), fixture.state(feedbackId).get("ownerToken").s());
        }
    }

    @Test
    void tokenAntigoNaoPodeConcluirLeaseReadquirido() {
        UUID feedbackId = UUID.randomUUID();
        fixture.putState(feedbackId, "PROCESSING", "stale-owner", NOW.minusSeconds(1).toString());
        ProcessingLease currentLease = gateway.tryStart(feedbackId).orElseThrow();

        assertThrows(
                ConditionalCheckFailedException.class,
                () -> gateway.markAboutToSend(feedbackId, new ProcessingLease("stale-owner")));

        gateway.markAboutToSend(feedbackId, currentLease);
        assertEquals("SEND_ATTEMPTED", fixture.state(feedbackId).get("status").s());
    }

    @Test
    void cicloCompletoHappyPathContraDynamoDb() {
        UUID feedbackId = UUID.randomUUID();

        ProcessingLease lease = gateway.tryStart(feedbackId).orElseThrow();
        assertEquals("PROCESSING", fixture.state(feedbackId).get("status").s());

        gateway.markAboutToSend(feedbackId, lease);
        assertEquals("SEND_ATTEMPTED", fixture.state(feedbackId).get("status").s());

        gateway.markSent(feedbackId, lease);
        assertEquals("SENT", fixture.state(feedbackId).get("status").s());
        assertTrue(fixture.state(feedbackId).containsKey("sentAt"));
    }

    @Test
    void markSentFuncionaDiretamenteDoEstadoProcessing() {
        UUID feedbackId = UUID.randomUUID();
        ProcessingLease lease = gateway.tryStart(feedbackId).orElseThrow();

        gateway.markSent(feedbackId, lease);

        assertEquals("SENT", fixture.state(feedbackId).get("status").s());
        assertTrue(fixture.state(feedbackId).containsKey("sentAt"));
    }

    @Test
    void markFailedBeforeSendContraDynamoDb() {
        UUID feedbackId = UUID.randomUUID();
        ProcessingLease lease = gateway.tryStart(feedbackId).orElseThrow();

        gateway.markFailedBeforeSend(feedbackId, lease, "connection timeout");

        assertEquals("FAILED_BEFORE_SEND", fixture.state(feedbackId).get("status").s());
        assertEquals("connection timeout", fixture.state(feedbackId).get("failureReason").s());
        assertTrue(fixture.state(feedbackId).containsKey("failedAt"));
    }

    @Test
    void markFailedAfterSendAttemptContraDynamoDb() {
        UUID feedbackId = UUID.randomUUID();
        ProcessingLease lease = gateway.tryStart(feedbackId).orElseThrow();
        gateway.markAboutToSend(feedbackId, lease);

        gateway.markFailedAfterSendAttempt(feedbackId, lease, "SES error");

        assertEquals("FAILED_AFTER_SEND_ATTEMPT", fixture.state(feedbackId).get("status").s());
        assertEquals("SES error", fixture.state(feedbackId).get("failureReason").s());
    }
}
