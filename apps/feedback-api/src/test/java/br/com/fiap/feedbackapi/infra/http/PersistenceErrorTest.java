package br.com.fiap.feedbackapi.infra.http;

import br.com.fiap.feedbackapi.core.exception.PersistenceException;
import br.com.fiap.feedbackplatform.shared.domain.Feedback;
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
class PersistenceErrorTest {
    @InjectMock
    FeedbackRepository feedbackRepository;

    @InjectMock
    CriticalFeedbackPublisher publisher;

    @Test
    void deveRetornarPersistenceErrorQuandoPersistenciaFalhar() {
        var correlationId = "persistence-error-test";

        doThrow(new PersistenceException("Falha ao persistir feedback.",
                new RuntimeException("DynamoDb indisponivel")))
                .when(feedbackRepository)
                .save(any(Feedback.class));

        given()
                .contentType("application/json")
                .header(HttpHeadersName.X_CORRELATION_ID, correlationId)
                .body("""
                        {
                          "descricao": "A aula foi interessante",
                          "nota": 8
                        }
                        """)
                .when().post("/avaliacao")
                .then()
                .statusCode(500)
                .header(HttpHeadersName.X_CORRELATION_ID, equalTo(correlationId))
                .body("code", equalTo("PERSISTENCE_ERROR"))
                .body("message", equalTo("Não foi possivel persistir o feedback."))
                .body("correlationId", equalTo(correlationId))
                .body("details", notNullValue());
    }
}
