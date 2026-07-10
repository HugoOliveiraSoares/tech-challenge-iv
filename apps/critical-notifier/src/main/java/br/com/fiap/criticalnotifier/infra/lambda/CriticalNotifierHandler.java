package br.com.fiap.criticalnotifier.infra.lambda;

import br.com.fiap.criticalnotifier.core.usecase.NotifyCriticalFeedbackUseCase;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import jakarta.inject.Named;
import java.util.UUID;

@Named("criticalNotifier")
public class CriticalNotifierHandler implements RequestHandler<CriticalNotifierHandler.Input, CriticalNotifierHandler.Output> {
    private final NotifyCriticalFeedbackUseCase notifyCriticalFeedbackUseCase;

    public CriticalNotifierHandler(NotifyCriticalFeedbackUseCase notifyCriticalFeedbackUseCase) {
        this.notifyCriticalFeedbackUseCase = notifyCriticalFeedbackUseCase;
    }

    @Override
    public Output handleRequest(Input input, Context context) {
        String feedbackId = input == null ? null : input.feedbackId();
        String correlationId = input == null ? null : input.correlationId();
        notifyCriticalFeedbackUseCase.execute(new CriticalFeedbackEvent(UUID.fromString(feedbackId), correlationId));
        return new Output("OK");
    }

    public record Input(String feedbackId, String correlationId) {
    }

    public record Output(String status) {
    }
}
