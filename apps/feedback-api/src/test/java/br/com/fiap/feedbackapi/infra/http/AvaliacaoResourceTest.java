package br.com.fiap.feedbackapi.infra.http;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AvaliacaoResourceTest {
    @Test
    void deveCriarAvaliacaoMinima() {
        given()
                .contentType("application/json")
                .body("{\"descricao\":\"A aula estava confusa e nao consegui acompanhar o conteudo.\",\"nota\":2}")
                .when().post("/avaliacao")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("status", equalTo("CREATED"))
                .body("urgencia", equalTo("CRITICA"))
                .body("dataEnvio", notNullValue());
    }
}
