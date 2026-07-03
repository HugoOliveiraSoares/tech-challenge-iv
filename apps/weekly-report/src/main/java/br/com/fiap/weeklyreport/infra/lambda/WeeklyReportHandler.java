package br.com.fiap.weeklyreport.infra.lambda;

import br.com.fiap.weeklyreport.core.domain.WeeklyReportRequest;
import br.com.fiap.weeklyreport.core.usecase.GenerateWeeklyReportUseCase;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import jakarta.inject.Named;

@Named("weeklyReport")
public class WeeklyReportHandler implements RequestHandler<WeeklyReportHandler.Input, WeeklyReportHandler.Output> {
    private final GenerateWeeklyReportUseCase generateWeeklyReportUseCase;

    public WeeklyReportHandler(GenerateWeeklyReportUseCase generateWeeklyReportUseCase) {
        this.generateWeeklyReportUseCase = generateWeeklyReportUseCase;
    }

    @Override
    public Output handleRequest(Input input, Context context) {
        String periodo = input == null ? null : input.periodo();
        generateWeeklyReportUseCase.execute(new WeeklyReportRequest(periodo));
        return new Output("OK");
    }

    public record Input(String periodo) {
    }

    public record Output(String status) {
    }
}
