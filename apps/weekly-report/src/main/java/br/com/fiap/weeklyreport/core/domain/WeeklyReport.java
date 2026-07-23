package br.com.fiap.weeklyreport.core.domain;

import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WeeklyReport(
        String periodo,
        double mediaGeral,
        Map<LocalDate, Long> quantidadePorDia,
        Map<Urgencia, Long> quantidadePorUrgencia,
        List<WeeklyFeedback> feedbacks,
        List<WeeklyFeedback> feedbacksCriticos) {
}
