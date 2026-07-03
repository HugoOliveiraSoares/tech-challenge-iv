package br.com.fiap.weeklyreport.infra.gateway.ses;

import br.com.fiap.weeklyreport.core.domain.WeeklyReportRequest;
import br.com.fiap.weeklyreport.core.gateway.ReportEmailGateway;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NoOpReportEmailGateway implements ReportEmailGateway {
    private static final Logger LOGGER = Logger.getLogger(NoOpReportEmailGateway.class);

    @Override
    public void sendWeeklyReport(WeeklyReportRequest request) {
        LOGGER.infof("Weekly report e-mail is not implemented yet. periodo=%s", request.periodo());
    }
}
