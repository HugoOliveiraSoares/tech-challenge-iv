package br.com.fiap.feedbackapi.infra.http;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.port.CriticalFeedbackPublisher;
import br.com.fiap.feedbackplatform.shared.port.FeedbackRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

@QuarkusTest
class AvaliacaoResourceTest {
    private static final String VALID_DESCRIPTION = "Avaliacao valida para testar o contrato HTTP";

    @InjectMock
    CriticalFeedbackPublisher criticalFeedbackPublisher;

    @InjectMock
    FeedbackRepository feedbackRepository;

    @Test
    void deveCriarAvaliacaoMinima() {
        doNothing()
                .when(criticalFeedbackPublisher)
                .publish(any(CriticalFeedbackEvent.class));

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
    void deveReutilizarCorrelationIdQuandoInformadoNoHeader() {
        given()
                .contentType("application/json")
                .header(HttpHeadersName.X_CORRELATION_ID, "correlation-test-123")
                .body("""
                          {
                            "descricao": "Testando reutilização do X-Correlation-Id informado",
                            "nota": 10
                          }
                        """)
                .when().post("/avaliacao")
                .then()
                .statusCode(201)
                .header(HttpHeadersName.X_CORRELATION_ID, equalTo("correlation-test-123"));
    }

    @Test
    void deveRetornar400QuandoCorrelationIdForMenorQue8Caracteres() {
        given()
                .contentType("application/json")
                .header(HttpHeadersName.X_CORRELATION_ID, "123")
                .body("""
                          {
                            "descricao": "Testando X-Correlation-Id menor que 8 caracteres",
                            "nota": 10
                          }
                        """)
                .when().post("/avaliacao")
                .then()
                .statusCode(400)
                .header(HttpHeadersName.X_CORRELATION_ID, equalTo("123"))
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("correlationId", equalTo("123"))
                .body("details[0].field", equalTo(HttpHeadersName.X_CORRELATION_ID))
                .body("details[0].message", equalTo("tamanho permitido entre 8 e 100 caracteres"));
    }

    @Test
    void deveRetornar400QuandoCriarAvaliacaoRequestBodyAusente() {
        given()
                .contentType("application/json")
                .when().post("/avaliacao")
                .then()
                .statusCode(400)
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("correlationId", notNullValue())
                .body("details[0].field", equalTo("request"));
    }

    @Test
    void deveRetornar400QuandoJsonMalformado() {
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
                .body("message", equalTo("Corpo da requisição não é um JSON válido"))
                .body("correlationId", notNullValue())
                .body("details", notNullValue());
    }

    @Test
    void deveRetornar400QuandoDescricaoAusente() {
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
    void deveRetornar400QuandoNotaAusente() {
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
    void deveRetornar422QuandoDescricaoCurta() {
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
                .body("details[0].message", notNullValue());
    }

    @Test
    void deveRetornar422QuandoNotaMenorQueZero() {
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
                .body("details[0].message", notNullValue());
    }

    @Test
    void deveRetornar422QuandoNotaMaiorQueDez() {
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
                .body("details[0].message", notNullValue());
    }

    @Test
    void deveRetornar422QuandoDescricaoNormalizadaForCurta() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "descricao": "    curta        ",
                          "nota": 10
                        }
                        """)
                .when().post("/avaliacao")
                .then()
                .statusCode(422)
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("code", equalTo("BUSINESS_RULE_ERROR"))
                .body("message", equalTo("Descrição deve ter pelo menos 10 caracteres."))
                .body("correlationId", notNullValue())
                .body("details", empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.5", "\"2\""})
    void deveRetornar400QuandoNotaNaoForInteira(String nota) {
        given()
                .contentType("application/json")
                .body("{\"descricao\":\"" + VALID_DESCRIPTION + "\",\"nota\":" + nota + "}")
                .when().post("/avaliacao")
                .then()
                .statusCode(400)
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", equalTo("Campo com tipo inválido no corpo da requisição"))
                .body("correlationId", notNullValue())
                .body("details[0].field", equalTo("nota"))
                .body("details[0].message", equalTo("tipo inválido"));
    }

    @ParameterizedTest
    @MethodSource("normalizedValidDescriptions")
    void deveAceitarDescricaoNoLimiteNormalizado(String descricao) {
        given()
                .contentType("application/json")
                .body("{\"descricao\":\"" + descricao + "\",\"nota\":10}")
                .when().post("/avaliacao")
                .then()
                .statusCode(201)
                .body("status", equalTo("CREATED"));
    }

    @ParameterizedTest
    @MethodSource("normalizedInvalidDescriptions")
    void deveRetornar422ParaDescricaoForaDoLimiteNormalizado(String descricao) {
        given()
                .contentType("application/json")
                .body("{\"descricao\":\"" + descricao + "\",\"nota\":10}")
                .when().post("/avaliacao")
                .then()
                .statusCode(422)
                .header(HttpHeadersName.X_CORRELATION_ID, notNullValue())
                .body("code", equalTo("BUSINESS_RULE_ERROR"))
                .body("correlationId", notNullValue());
    }

    @ParameterizedTest
    @MethodSource("urgencyBoundaries")
    void deveClassificarTodosOsLimitesDeUrgencia(int nota, String urgencia) {
        given()
                .contentType("application/json")
                .body("{\"descricao\":\"" + VALID_DESCRIPTION + "\",\"nota\":" + nota + "}")
                .when().post("/avaliacao")
                .then()
                .statusCode(201)
                .body("urgencia", equalTo(urgencia));
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 100})
    void deveAceitarCorrelationIdNosLimites(int length) {
        String correlationId = "c".repeat(length);

        given()
                .contentType("application/json")
                .header(HttpHeadersName.X_CORRELATION_ID, correlationId)
                .body("{\"descricao\":\"" + VALID_DESCRIPTION + "\",\"nota\":10}")
                .when().post("/avaliacao")
                .then()
                .statusCode(201)
                .header(HttpHeadersName.X_CORRELATION_ID, equalTo(correlationId));
    }

    @Test
    void deveRetornar400QuandoCorrelationIdForMaiorQue100Caracteres() {
        String correlationId = "c".repeat(101);

        given()
                .contentType("application/json")
                .header(HttpHeadersName.X_CORRELATION_ID, correlationId)
                .body("{\"descricao\":\"" + VALID_DESCRIPTION + "\",\"nota\":10}")
                .when().post("/avaliacao")
                .then()
                .statusCode(400)
                .header(HttpHeadersName.X_CORRELATION_ID, equalTo(correlationId))
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("correlationId", equalTo(correlationId))
                .body("details[0].field", equalTo(HttpHeadersName.X_CORRELATION_ID));
    }

    private static Stream<String> normalizedValidDescriptions() {
        return Stream.of("  " + "a".repeat(10) + "  ", "a".repeat(1000));
    }

    private static Stream<String> normalizedInvalidDescriptions() {
        return Stream.of("  " + "a".repeat(9) + "  ", "a".repeat(1001));
    }

    private static Stream<Arguments> urgencyBoundaries() {
        return Stream.of(
                Arguments.of(0, "CRITICA"),
                Arguments.of(3, "CRITICA"),
                Arguments.of(4, "MEDIA"),
                Arguments.of(6, "MEDIA"),
                Arguments.of(7, "BAIXA"),
                Arguments.of(10, "BAIXA"));
    }
}
