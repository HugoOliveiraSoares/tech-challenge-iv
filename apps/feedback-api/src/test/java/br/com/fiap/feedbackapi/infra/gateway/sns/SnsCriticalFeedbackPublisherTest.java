package br.com.fiap.feedbackapi.infra.gateway.sns;

import br.com.fiap.feedbackapi.core.exception.NotificationException;
import br.com.fiap.feedbackplatform.shared.domain.CriticalFeedbackEvent;
import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnsCriticalFeedbackPublisherTest {
    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:critical-feedback";

    @Mock
    SnsClient snsClient;

    ObjectMapper objectMapper;

    private SnsCriticalFeedbackPublisher publisher;

    @BeforeEach
    void setup(){
        objectMapper = spy(
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

        publisher = new SnsCriticalFeedbackPublisher(snsClient,
                objectMapper,
                TOPIC_ARN);
    }

    @Test
    void devePublicarEventoCriticoNoSns() throws JsonProcessingException{
        var event = criarEvento();
        var eventJson = """
                {
                    "eventType": "FeedbackCritico",
                    "feedbackId": "123"
                }
                """;

        doReturn(eventJson)
                .when(objectMapper).writeValueAsString(event);

        when(snsClient.publish(ArgumentMatchers.any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder()
                        .messageId("message-123")
                        .build());

        publisher.publish(event);

        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);

        verify(snsClient).publish(requestCaptor.capture());

        PublishRequest request = requestCaptor.getValue();

        assertEquals(TOPIC_ARN, request.topicArn());
        assertEquals(eventJson, request.message());
    }

    @Test
    void devePublicarEventoCriticoComoJson() throws Exception{
        var event = criarEvento();

        when(snsClient.publish(ArgumentMatchers.any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder()
                        .messageId("message-11111111-1111-1111-1111-111111111111")
                        .build());

        publisher.publish(event);

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);

        verify(snsClient).publish(captor.capture());

        PublishRequest request = captor.getValue();

        assertEquals(TOPIC_ARN, request.topicArn());

        JsonNode json = objectMapper.readTree(request.message());

        assertAll(
                ()-> assertEquals("FeedbackCritico", json.get("eventType").asText()),
                ()-> assertEquals("1.0", json.get("eventVersion").asText()),
                ()-> assertEquals("11111111-1111-1111-1111-111111111111", json.get("feedbackId").asText()),
                ()-> assertEquals("A aula estava confusa e nao consegui acompanhar", json.get("descricao").asText()),
                ()-> assertEquals(2, json.get("nota").asInt()),
                ()-> assertEquals("CRITICA", json.get("urgencia").asText()),
                ()-> assertEquals("2026-01-01T10:00:00Z", json.get("dataEnvio").asText()),
                ()-> assertEquals("2026-W01",json.get("periodo").asText()),
                ()-> assertEquals("correlation-123", json.get("correlationId").asText())
        );
    }

    @Test
    void deveLancarNotificationExceptionQuandoFalharSerializacao() throws JsonProcessingException {
        var event = criarEvento();
        var jsonException = new JsonProcessingException("Erro de serializacao"){};

        doThrow(jsonException)
                .when(objectMapper).writeValueAsString(event);

        NotificationException exception = assertThrows(NotificationException.class,
                ()-> publisher.publish(event));

        assertEquals("Falha ao serializar evento de feedback critico", exception.getMessage());
        assertSame(jsonException, exception.getCause());

        verify(objectMapper).writeValueAsString(event);
        verifyNoMoreInteractions(snsClient);
    }

    @Test
    void deveLancarNotificationExceptionQuandoFalharPublicacao() throws JsonProcessingException {
        var event = criarEvento();
        var eventJson = """
                {
                    "eventType": "FeedbackCritico"
                }
                """;
        var snsException = SnsException.builder()
                .message("SNS indisponivel")
                .build();

        doReturn(eventJson)
                .when(objectMapper).writeValueAsString(event);

        doThrow(snsException)
                .when(snsClient).publish(ArgumentMatchers.any(PublishRequest.class));

        NotificationException exception = assertThrows(NotificationException.class,
                ()-> publisher.publish(event));

        assertEquals("Falha ao publicar evento de feedback critico", exception.getMessage());
        assertSame(snsException, exception.getCause());

        verify(objectMapper).writeValueAsString(event);
        verify(snsClient).publish(ArgumentMatchers.any(PublishRequest.class));
        verifyNoMoreInteractions(snsClient);
    }

    private CriticalFeedbackEvent criarEvento() {
        return new CriticalFeedbackEvent("FeedbackCritico",
                "1.0",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "A aula estava confusa e nao consegui acompanhar",
                2,
                Urgencia.CRITICA,
                Instant.parse("2026-01-01T10:00:00Z"),
                "2026-W01",
                "correlation-123");
    }
}
