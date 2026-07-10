package br.com.fiap.criticalnotifier.core.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotifyCriticalFeedbackUseCaseTest {
    @Test
    void delegaEnvioParaGateway() {
        List<CriticalFeedbackEvent> sent = new ArrayList<>();
        NotifyCriticalFeedbackUseCase useCase = new NotifyCriticalFeedbackUseCase(sent::add);
        CriticalFeedbackEvent notification = new CriticalFeedbackEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "correlation-1");

        useCase.execute(notification);

        assertEquals(List.of(notification), sent);
    }
}
