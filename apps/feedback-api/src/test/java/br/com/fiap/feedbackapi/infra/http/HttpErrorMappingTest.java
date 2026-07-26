package br.com.fiap.feedbackapi.infra.http;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class HttpErrorMappingTest {

    @Test
    void devePreservar404EoCorrelationIdParaRotaInexistente() {
        var correlationId = "not-found-test";

        given()
                .header(HttpHeadersName.X_CORRELATION_ID, correlationId)
                .when().get("/rota-inexistente")
                .then()
                .statusCode(404)
                .header(HttpHeadersName.X_CORRELATION_ID, equalTo(correlationId));
    }

    @Test
    void devePreservar415EoCorrelationIdParaContentTypeIncompativel() {
        var correlationId = "unsupported-media-test";

        given()
                .contentType("text/plain")
                .header(HttpHeadersName.X_CORRELATION_ID, correlationId)
                .body("conteudo sem representacao JSON")
                .when().post("/avaliacao")
                .then()
                .statusCode(415)
                .header(HttpHeadersName.X_CORRELATION_ID, equalTo(correlationId));
    }
}
