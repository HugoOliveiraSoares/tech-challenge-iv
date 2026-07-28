package br.com.fiap.feedbackapi.infra.http;

import br.com.fiap.feedbackapi.core.exception.NotificationException;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.port.CriticalFeedbackPublisher;
import br.com.fiap.feedbackplatform.shared.port.FeedbackRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@QuarkusTest
class NotificationErrorTest {
    @InjectMock
    FeedbackRepository feedbackRepository;

    @InjectMock
    CriticalFeedbackPublisher publisher;

    @Test
    void deveRetornarNotificationErrorQuandoPublicacaoFalhar() {
        var correlationId = "notication-error-tes";

        doThrow(new NotificationException("Falha ao publicar evento de feedback critico",
                new RuntimeException("SNS indisponivel")))
                .when(publisher)
                .publish(any(CriticalFeedbackEvent.class));

        given()
                .contentType("application/json")
                .header(HttpHeadersName.X_CORRELATION_ID, correlationId)
                .body("""
                 {
                   "descricao": "A aula esta confusa e não consegui acompanhar.",
                   "nota": 2                 
                 }
                 """)
                .when().post("/avaliacao")
                .then()
                .statusCode(500)
                .header(HttpHeadersName.X_CORRELATION_ID, equalTo(correlationId))
                .body("code", equalTo("NOTIFICATION_ERROR"))
                .body("message", equalTo("Não foi possivel publicar a notificação critica."))
                .body("correlationId", equalTo(correlationId))
                .body("details", notNullValue());
    }
}
