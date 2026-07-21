package br.com.fiap.weeklyreport.core.gateway;

import br.com.fiap.weeklyreport.core.domain.WeeklyFeedback;
import java.util.List;

public interface WeeklyFeedbackReader {
    List<WeeklyFeedback> findByPeriodo(String periodo);
}
