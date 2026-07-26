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
}
