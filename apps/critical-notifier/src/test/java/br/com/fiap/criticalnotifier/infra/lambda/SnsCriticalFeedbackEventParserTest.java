package br.com.fiap.criticalnotifier.infra.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import br.com.fiap.feedbackplatform.shared.exception.DomainValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SnsCriticalFeedbackEventParserTest {
    private SnsCriticalFeedbackEventParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        parser = new SnsCriticalFeedbackEventParser(objectMapper);
    }

    @Test
    void desserializaEventoCriticoValido() {
        String message =
                """
                {
                  "feedbackId": "11111111-1111-1111-1111-111111111111",
                  "correlationId": "correlation-1",
                  "descricao": "A aula estava confusa e nao consegui acompanhar o conteudo.",
                  "nota": 2,
                  "urgencia": "CRITICA",
                  "dataEnvio": "2026-05-31T13:00:00Z"
                }
                """;

        CriticalFeedbackEvent event = parser.parse(message);

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), event.feedbackId());
        assertEquals("correlation-1", event.correlationId());
        assertEquals("A aula estava confusa e nao consegui acompanhar o conteudo.", event.descricao());
        assertEquals(2, event.nota());
        assertEquals(Urgencia.CRITICA, event.urgencia());
        assertEquals(Instant.parse("2026-05-31T13:00:00Z"), event.dataEnvio());
    }

    @Test
    void rejeitaMensagemInvalida() {
        assertThrows(DomainValidationException.class, () -> parser.parse("{"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "{\"nota\":2,\"descricao\":\"Descricao critica valida\",\"urgencia\":\"CRITICA\",\"dataEnvio\":\"2026-05-31T13:00:00Z\"}",
        "{\"feedbackId\":\"11111111-1111-1111-1111-111111111111\",\"descricao\":\"Descricao critica valida\",\"urgencia\":\"CRITICA\",\"dataEnvio\":\"2026-05-31T13:00:00Z\"}",
        "{\"feedbackId\":\"not-a-uuid\",\"nota\":2,\"descricao\":\"Descricao critica valida\",\"urgencia\":\"CRITICA\",\"dataEnvio\":\"2026-05-31T13:00:00Z\"}"
    })
    void rejeitaMensagemVaziaOuSemCamposObrigatorios(String message) {
        assertThrows(DomainValidationException.class, () -> parser.parse(message));
    }

    @Test
    void rejeitaMensagemComFeedbackIdNulo() {
        String message = """
                {
                  "feedbackId": null,
                  "nota": 2,
                  "descricao": "Descricao valida.",
                  "urgencia": "CRITICA",
                  "dataEnvio": "2026-05-31T13:00:00Z"
                }
                """;
        assertThrows(DomainValidationException.class, () -> parser.parse(message));
    }

    @Test
    void rejeitaMensagemSemNota() {
        String message = """
                {
                  "feedbackId": "11111111-1111-1111-1111-111111111111",
                  "descricao": "Descricao valida.",
                  "urgencia": "CRITICA",
                  "dataEnvio": "2026-05-31T13:00:00Z"
                }
                """;
        assertThrows(DomainValidationException.class, () -> parser.parse(message));
    }

    @Test
    void rejeitaMensagemNula() {
        assertThrows(DomainValidationException.class, () -> parser.parse(null));
    }

    @Test
    void ignoraCamposDesconhecidosNaMensagem() {
        String message = """
                {
                  "feedbackId": "11111111-1111-1111-1111-111111111111",
                  "correlationId": "corr-1",
                  "descricao": "Descricao valida.",
                  "nota": 5,
                  "urgencia": "MEDIA",
                  "dataEnvio": "2026-05-31T13:00:00Z",
                  "unknownField": "ignored"
                }
                """;

        CriticalFeedbackEvent event = parser.parse(message);

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), event.feedbackId());
        assertEquals(5, event.nota());
        assertEquals(Urgencia.MEDIA, event.urgencia());
    }
}
