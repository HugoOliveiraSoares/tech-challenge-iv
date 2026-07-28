package br.com.fiap.criticalnotifier.core.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CriticalNotificationEmailComposerTest {

    private static final UUID FEEDBACK_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final Instant DATA_ENVIO =
            Instant.parse("2026-05-31T13:00:00Z");

    private static final String PERIODO = "2026-W22";

    private final CriticalNotificationEmailComposer composer =
            new CriticalNotificationEmailComposer();

    @Test
    void montaEmailComDescricaoUrgenciaDataEnvioERastreabilidade() {
        CriticalFeedbackEvent event = new CriticalFeedbackEvent(
                "FeedbackCritico",
                "1.0",
                FEEDBACK_ID,
                "A aula estava confusa e nao consegui acompanhar o conteudo.",
                2,
                Urgencia.CRITICA,
                DATA_ENVIO,
                PERIODO,
                "correlation-1"
        );

        CriticalNotificationEmail email = composer.compose(event);

        assertTrue(email.subject().contains("feedbackId=" + FEEDBACK_ID));
        assertTrue(email.subject().contains("correlationId=correlation-1"));
        assertTrue(email.body().contains(
                "Descricao: A aula estava confusa e nao consegui acompanhar o conteudo."
        ));
        assertTrue(email.body().contains("Urgencia: CRITICA"));
        assertTrue(email.body().contains(
                "Data de envio: 2026-05-31 13:00:00 UTC"
        ));
        assertTrue(email.body().contains("feedbackId: " + FEEDBACK_ID));
        assertTrue(email.body().contains("nota: 2"));
        assertTrue(email.body().contains("correlationId: correlation-1"));
    }

    @Test
    void montaEmailSemCorrelationIdNoCorpoQuandoAusente() {
        CriticalFeedbackEvent event = new CriticalFeedbackEvent(
                "FeedbackCritico",
                "1.0",
                FEEDBACK_ID,
                "Descricao valida para alerta critico.",
                1,
                Urgencia.CRITICA,
                DATA_ENVIO,
                PERIODO,
                null
        );

        CriticalNotificationEmail email = composer.compose(event);

        assertTrue(email.subject().contains("feedbackId=" + FEEDBACK_ID));
        assertTrue(!email.subject().contains("correlationId="));
        assertTrue(!email.body().contains("correlationId:"));
    }
}