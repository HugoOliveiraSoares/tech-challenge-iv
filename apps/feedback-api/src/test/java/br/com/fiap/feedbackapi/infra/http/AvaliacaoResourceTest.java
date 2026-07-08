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
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("id", notNullValue())
                .body("status", equalTo("CREATED"))
                .body("urgencia", equalTo("CRITICA"))
                .body("dataEnvio", notNullValue());
    }

    @Test
    void deveRetornar400QuandoJsonMalformado(){
        given()
                .contentType("application/json")
                .body("""
                  {
                    "descricao": "Testando Json mal formado",
                    "nota":
                  }
                  """)
                .when().post("/avaliacao")
                .then()
                .statusCode(400)
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("code", equalTo("MALFORMED_JSON"))
                .body("message", equalTo("Corpo da requisição nao é um JSON válido"))
                .body("correlationId", notNullValue())
                .body("details", notNullValue());
    }

    @Test
    void deveRetornar400QuandoDescricaoAusente(){
        given()
                .contentType("application/json")
                .body("""
                  {
                    "nota": 8
                  }
                  """)
                .when().post("/avaliacao")
                .then()
                .statusCode(400)
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", equalTo("Campos obrigatórios ausentes ou inválidos"))
                .body("correlationId", notNullValue())
                .body("details", notNullValue());
    }

    @Test
    void deveRetornar400QuandoNotaAusente(){
        given()
                .contentType("application/json")
                .body("""
                  {
                    "descricao": "Testando nota ausente"
                  }
                  """)
                .when().post("/avaliacao")
                .then()
                .statusCode(400)
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", equalTo("Campos obrigatórios ausentes ou inválidos"))
                .body("correlationId", notNullValue())
                .body("details", notNullValue());
    }

    @Test
    void deveRetornar422QuandoDescricaoCurta(){
        given()
                .contentType("application/json")
                .body("""
                  {
                    "descricao": "Hi",
                    "nota": 9
                  }
                  """)
                .when().post("/avaliacao")
                .then()
                .statusCode(422)
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("code", equalTo("BUSINESS_RULE_ERROR"))
                .body("message", equalTo("Regra de negocio violada"))
                .body("correlationId", notNullValue())
                .body("details[0].field", equalTo("descricao"))
                .body("details[0].message", equalTo("size must be between 10 and 1000"));
    }

    @Test
    void deveRetornar422QuandoNotaMenorQueZero(){
        given()
                .contentType("application/json")
                .body("""
                  {
                    "descricao": "Testando nota menor que zero",
                    "nota": -1
                  }
                  """)
                .when().post("/avaliacao")
                .then()
                .statusCode(422)
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("code", equalTo("BUSINESS_RULE_ERROR"))
                .body("message", equalTo("Regra de negocio violada"))
                .body("correlationId", notNullValue())
                .body("details[0].field", equalTo("nota"))
                .body("details[0].message", equalTo("must be greater than or equal to 0"));
    }

    @Test
    void deveRetornar422QuandoNotaMaiorQueDez(){
        given()
                .contentType("application/json")
                .body("""
                  {
                    "descricao": "Testando nota maior que dez",
                    "nota": 11
                  }
                  """)
                .when().post("/avaliacao")
                .then()
                .statusCode(422)
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("code", equalTo("BUSINESS_RULE_ERROR"))
                .body("message", equalTo("Regra de negocio violada"))
                .body("correlationId", notNullValue())
                .body("details[0].field", equalTo("nota"))
                .body("details[0].message", equalTo("must be less than or equal to 10"));
    }
}