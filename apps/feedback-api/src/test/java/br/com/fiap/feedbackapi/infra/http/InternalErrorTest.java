package br.com.fiap.feedbackapi.infra.http;

import br.com.fiap.feedbackapi.core.dto.CriarAvaliacaoCommand;
import br.com.fiap.feedbackapi.core.usecase.CriarAvaliacaoUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class InternalErrorTest {

    @InjectMock
    CriarAvaliacaoUseCase criarAvaliacaoUseCase;

    @Test
    void deveRetornar500QuandoOcorrerErroInesperado(){
        when(criarAvaliacaoUseCase.execute(any(CriarAvaliacaoCommand.class)))
                .thenThrow(new IllegalStateException("Erro inesperado"));

        given()
                .contentType("application/json")
                .body("""
                 {
                    "descricao": "Testando erro inesperado na aplicacao",
                    "nota": 10
                 }
                 """)
                .when().post("/avaliacao")
                .then()
                .statusCode(500)
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("code", equalTo("INTERNAL_ERROR"))
                .body("message", equalTo("Erro interno ao processar a requisição"))
                .body("correlationId", notNullValue())
                .body("details", notNullValue());

    }
}
