package br.com.fiap.feedbackapi.core.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.fiap.feedbackapi.core.dto.CriarAvaliacaoCommand;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.domain.Feedback;
import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import br.com.fiap.feedbackplatform.shared.port.CriticalFeedbackPublisher;
import br.com.fiap.feedbackplatform.shared.port.FeedbackRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class CriarAvaliacaoUseCaseTest {
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-27T14:30:00Z");
    private static final String CORRELATION_ID = "correlation-use-case-123";

    private FeedbackRepository feedbackRepository;
    private CriticalFeedbackPublisher criticalFeedbackPublisher;
    private CriarAvaliacaoUseCase useCase;

    @BeforeEach
    void setUp() {
        feedbackRepository = mock(FeedbackRepository.class);
        criticalFeedbackPublisher = mock(CriticalFeedbackPublisher.class);
        useCase = new CriarAvaliacaoUseCase(
                feedbackRepository,
                criticalFeedbackPublisher,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    void devePersistirEPublicarEventoCompletoParaAvaliacaoCritica() {
        var command = new CriarAvaliacaoCommand("  Conteudo dificil de acompanhar  ", 3, CORRELATION_ID);

        Feedback result = useCase.execute(command);

        var feedbackCaptor = ArgumentCaptor.forClass(Feedback.class);
        var eventCaptor = ArgumentCaptor.forClass(CriticalFeedbackEvent.class);
        var order = inOrder(feedbackRepository, criticalFeedbackPublisher);
        order.verify(feedbackRepository).save(feedbackCaptor.capture());
        order.verify(criticalFeedbackPublisher).publish(eventCaptor.capture());

        Feedback persisted = feedbackCaptor.getValue();
        CriticalFeedbackEvent event = eventCaptor.getValue();
        assertSame(result, persisted);
        assertEquals("Conteudo dificil de acompanhar", persisted.descricao());
        assertEquals(3, persisted.nota());
        assertEquals(Urgencia.CRITICA, persisted.urgencia());
        assertEquals(FIXED_INSTANT, persisted.dataEnvio());
        assertEquals("2026-W31", persisted.periodo());
        assertEquals(CORRELATION_ID, persisted.correlationId());
        assertEquals(persisted.id(), event.feedbackId());
        assertEquals(persisted.correlationId(), event.correlationId());
        assertEquals(persisted.descricao(), event.descricao());
        assertEquals(persisted.nota(), event.nota());
        assertEquals(persisted.urgencia(), event.urgencia());
        assertEquals(persisted.dataEnvio(), event.dataEnvio());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 7})
    void devePersistirSemPublicarAvaliacaoMediaOuBaixa(int nota) {
        Feedback result = useCase.execute(
                new CriarAvaliacaoCommand("Avaliacao valida para persistencia", nota, CORRELATION_ID));

        verify(feedbackRepository).save(result);
        verify(criticalFeedbackPublisher, never()).publish(any());
        assertEquals(nota == 4 ? Urgencia.MEDIA : Urgencia.BAIXA, result.urgencia());
        assertEquals(FIXED_INSTANT, result.dataEnvio());
    }

    @Test
    void devePropagarFalhaDePersistenciaSemPublicar() {
        var failure = new IllegalStateException("persistence failed");
        doThrow(failure).when(feedbackRepository).save(any());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> useCase.execute(new CriarAvaliacaoCommand(
                        "Avaliacao critica que falha ao persistir", 0, CORRELATION_ID)));

        assertSame(failure, thrown);
        verify(criticalFeedbackPublisher, never()).publish(any());
    }

    @Test
    void devePropagarFalhaDePublicacaoDepoisDePersistir() {
        var failure = new IllegalStateException("publication failed");
        doThrow(failure).when(criticalFeedbackPublisher).publish(any());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> useCase.execute(new CriarAvaliacaoCommand(
                        "Avaliacao critica que falha ao publicar", 3, CORRELATION_ID)));

        assertSame(failure, thrown);
        var feedbackCaptor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(feedbackCaptor.capture());
        assertEquals(FIXED_INSTANT, feedbackCaptor.getValue().dataEnvio());
    }
}
