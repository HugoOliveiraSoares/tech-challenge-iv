package br.com.fiap.weeklyreport.core.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.fiap.weeklyreport.core.domain.WeeklyReportRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerateWeeklyReportUseCaseTest {
    @Test
    void delegaEnvioParaGateway() {
        List<WeeklyReportRequest> sent = new ArrayList<>();
        GenerateWeeklyReportUseCase useCase = new GenerateWeeklyReportUseCase(sent::add);
        WeeklyReportRequest request = new WeeklyReportRequest("2026-W26");

        useCase.execute(request);

        assertEquals(List.of(request), sent);
    }
}
