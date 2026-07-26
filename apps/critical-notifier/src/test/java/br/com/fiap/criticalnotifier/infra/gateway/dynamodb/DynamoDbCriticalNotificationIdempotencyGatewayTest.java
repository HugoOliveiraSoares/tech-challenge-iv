package br.com.fiap.criticalnotifier.infra.gateway.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@ExtendWith(MockitoExtension.class)
class DynamoDbCriticalNotificationIdempotencyGatewayTest {
    private static final UUID FEEDBACK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-31T13:00:00Z"), ZoneOffset.UTC);

    @Mock
    DynamoDbClient dynamoDbClient;

    private DynamoDbCriticalNotificationIdempotencyGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new DynamoDbCriticalNotificationIdempotencyGateway(
                dynamoDbClient, "feedback-processing-control-local", CLOCK);
    }

    @Test
    void usaChaveComPrefixoCriticalNoCampoPeriodo() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(null);

        assertTrue(gateway.tryStart(FEEDBACK_ID));

        ArgumentCaptor<UpdateItemRequest> requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(requestCaptor.capture());
        assertEquals("critical#11111111-1111-1111-1111-111111111111", requestCaptor.getValue().key().get("periodo").s());
    }

    @Test
    void bloqueiaReprocessamentoQuandoEscritaCondicionalFalha() {
        doThrow(ConditionalCheckFailedException.builder().build())
                .when(dynamoDbClient)
                .updateItem(any(UpdateItemRequest.class));

        assertFalse(gateway.tryStart(FEEDBACK_ID));
    }
}
