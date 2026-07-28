package br.com.fiap.weeklyreport.infra.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.weeklyreport.core.domain.WeeklyReportRequest;
import br.com.fiap.weeklyreport.core.domain.WeeklyReportResult;
import br.com.fiap.weeklyreport.core.usecase.GenerateWeeklyReportUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyReportHandlerTest {
    @Mock
    GenerateWeeklyReportUseCase useCase;

    private WeeklyReportHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WeeklyReportHandler(useCase);
    }

    @Test
    void nullInputForwardsNullPeriodAndReturnsSentOutput() {
        when(useCase.execute(any())).thenReturn(new WeeklyReportResult("2026-W26", true, "SENT"));

        WeeklyReportHandler.Output output = handler.handleRequest(null, null);

        ArgumentCaptor<WeeklyReportRequest> request = ArgumentCaptor.forClass(WeeklyReportRequest.class);
        verify(useCase).execute(request.capture());
        assertEquals(null, request.getValue().periodo());
        assertEquals("SENT", output.status());
        assertEquals("2026-W26", output.periodo());
        assertTrue(output.sent());
    }

    @Test
    void explicitPeriodIsForwardedAndSkippedOutputIsPreserved() {
        when(useCase.execute(any())).thenReturn(new WeeklyReportResult("2026-W25", false, "SKIPPED"));

        WeeklyReportHandler.Output output = handler.handleRequest(new WeeklyReportHandler.Input("2026-W25"), null);

        ArgumentCaptor<WeeklyReportRequest> request = ArgumentCaptor.forClass(WeeklyReportRequest.class);
        verify(useCase).execute(request.capture());
        assertEquals("2026-W25", request.getValue().periodo());
        assertEquals("SKIPPED", output.status());
        assertFalse(output.sent());
    }

    @Test
    void propagatesUseCaseException() {
        IllegalStateException failure = new IllegalStateException("reader unavailable");
        when(useCase.execute(any())).thenThrow(failure);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> handler.handleRequest(new WeeklyReportHandler.Input("2026-W26"), null));

        assertSame(failure, thrown);
    }
}
