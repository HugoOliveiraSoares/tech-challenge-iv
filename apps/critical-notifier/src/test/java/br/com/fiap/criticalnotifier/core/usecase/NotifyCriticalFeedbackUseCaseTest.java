package br.com.fiap.criticalnotifier.core.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.fiap.criticalnotifier.core.domain.CriticalFeedbackNotification;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotifyCriticalFeedbackUseCaseTest {
    @Test
    void delegaEnvioParaGateway() {
        List<CriticalFeedbackNotification> sent = new ArrayList<>();
        NotifyCriticalFeedbackUseCase useCase = new NotifyCriticalFeedbackUseCase(sent::add);
        CriticalFeedbackNotification notification = new CriticalFeedbackNotification("feedback-1", "correlation-1");

        useCase.execute(notification);

        assertEquals(List.of(notification), sent);
    }
}
