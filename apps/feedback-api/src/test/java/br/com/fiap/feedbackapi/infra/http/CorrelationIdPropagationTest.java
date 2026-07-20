package br.com.fiap.feedbackapi.infra.http;

import br.com.fiap.feedbackapi.core.dto.CriarAvaliacaoCommand;
import br.com.fiap.feedbackapi.core.usecase.CriarAvaliacaoUseCase;
import br.com.fiap.feedbackplatform.shared.domain.Feedback;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class CorrelationIdPropagationTest {

    @InjectMock
    CriarAvaliacaoUseCase criarAvaliacaoUseCase;

    @Test
    void devePropagarCorrelationIdGeradoParaOCommand() {
        when(criarAvaliacaoUseCase.execute(any(CriarAvaliacaoCommand.class)))
                .thenAnswer(invocation -> {
                    CriarAvaliacaoCommand command = invocation.getArgument(0);

                    return Feedback.criar(UUID.randomUUID(),
                            command.descricao(),
                            command.nota(),
                            Instant.now(),
                            command.correlationId());
                });

        var correlationIdResponse = given()
                .contentType("application/json")
                .body("""
                        {
                          "descricao": "Testando propagação do correlation id gerado",
                          "nota": 10
                        }
                        """)
                .when()
                .post("/avaliacao")
                .then()
                .statusCode(201)
                .extract()
                .header(HttpHeadersName.X_CORRELATION_ID);

        ArgumentCaptor<CriarAvaliacaoCommand> captor = ArgumentCaptor.forClass(CriarAvaliacaoCommand.class);

        verify(criarAvaliacaoUseCase).execute(captor.capture());

        CriarAvaliacaoCommand commandCapturado = captor.getValue();

        assertNotNull(correlationIdResponse);
        assertNotNull(commandCapturado.correlationId());
        assertEquals(correlationIdResponse,
                commandCapturado.correlationId());
    }

    @Test
    void devePropagarCorrelationIdInformadoNoHeaderParaOCommand() {
        when(criarAvaliacaoUseCase.execute(any(CriarAvaliacaoCommand.class)))
                .thenAnswer(invocation -> {
                    CriarAvaliacaoCommand command = invocation.getArgument(0);

                    return Feedback.criar(UUID.randomUUID(),
                            command.descricao(),
                            command.nota(),
                            Instant.now(),
                            command.correlationId());
                });

        var correlationIdResponse = given()
                .contentType("application/json")
                .header(HttpHeadersName.X_CORRELATION_ID, "correlation-test-123")
                .body("""
                        {
                          "descricao": "Testando propagação correlation id informado no header",
                          "nota": 10
                        }
                        """)
                .when()
                .post("/avaliacao")
                .then()
                .statusCode(201)
                .extract()
                .header(HttpHeadersName.X_CORRELATION_ID);

        ArgumentCaptor<CriarAvaliacaoCommand> captor = ArgumentCaptor.forClass(CriarAvaliacaoCommand.class);

        verify(criarAvaliacaoUseCase).execute(captor.capture());

        CriarAvaliacaoCommand commandCapturado = captor.getValue();

        assertNotNull(correlationIdResponse);
        assertNotNull(commandCapturado.correlationId());
        assertEquals("correlation-test-123",
                commandCapturado.correlationId());
        assertEquals("correlation-test-123", correlationIdResponse);
        assertEquals(correlationIdResponse, commandCapturado.correlationId());
    }
}
