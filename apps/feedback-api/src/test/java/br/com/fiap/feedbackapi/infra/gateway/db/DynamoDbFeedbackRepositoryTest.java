package br.com.fiap.feedbackapi.infra.gateway.db;


import br.com.fiap.feedbackapi.core.exception.PersistenceException;
import br.com.fiap.feedbackplatform.shared.domain.Feedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DynamoDbFeedbackRepositoryTest {

    private static final String TABLE_NAME = "feedbacks";
    private static final UUID FEEDBACK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant DATA_ENVIO = Instant.parse("2026-01-01T10:00:00Z");

    private DynamoDbClient dynamoDbClient;
    private DynamoDbFeedbackRepository repository;

    @BeforeEach
    void setup() {
        dynamoDbClient = mock(DynamoDbClient.class);
        repository = new DynamoDbFeedbackRepository(dynamoDbClient, TABLE_NAME);

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());
    }

    @Test
    void devePersistirFeedbackComTodosOsAtributos(){
        var feedback = Feedback.criar(FEEDBACK_ID,
                "A aula estava confusa e nao consegui acompanhar",
                2,
                DATA_ENVIO,
                "correlation-123");

        repository.save(feedback);

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);

        verify(dynamoDbClient).putItem(captor.capture());

        PutItemRequest request = captor.getValue();

        assertAll(
                ()-> assertEquals(TABLE_NAME, request.tableName()),
                ()-> assertEquals(FEEDBACK_ID.toString(), request.item().get("id").s()),
                ()-> assertEquals("A aula estava confusa e nao consegui acompanhar", request.item().get("descricao").s()),
                ()-> assertEquals("2", request.item().get("nota").n()),
                ()-> assertEquals("CRITICA", request.item().get("urgencia").s()),
                ()-> assertEquals(DATA_ENVIO.toString(), request.item().get("dataEnvio").s()),
                ()-> assertEquals("2026-W01", request.item().get("periodo").s()),
                ()-> assertEquals("correlation-123", request.item().get("correlationId").s())
        );
    }

    @Test
    void deveLancarPersistenceExceptionQuandoDynamoDbFalhar(){
        var feedback = Feedback.criar(FEEDBACK_ID,
                "A aula estava confusa e nao consegui acompanhar",
                2,
                DATA_ENVIO,
                "correlation-123");
        var dynamoDbException = DynamoDbException.builder()
                .message("DynamoDb indisponivel")
                .build();

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(dynamoDbException);

        PersistenceException exception = assertThrows(
                PersistenceException.class,
                ()-> repository.save(feedback)
        );

        assertEquals("Falha ao persistir feedback.", exception.getMessage());
        assertSame(dynamoDbException, exception.getCause());
    }
}
