package br.com.fiap.criticalnotifier.infra.gateway.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.criticalnotifier.core.domain.ProcessingLease;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
    private static final Instant NOW = Instant.parse("2026-05-31T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    DynamoDbClient dynamoDbClient;

    private DynamoDbCriticalNotificationIdempotencyGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new DynamoDbCriticalNotificationIdempotencyGateway(
                dynamoDbClient, "feedback-processing-control-local", CLOCK, 60);
    }

    @Test
    void usaChaveComPrefixoCriticalNoCampoPeriodo() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(null);

        assertTrue(gateway.tryStart(FEEDBACK_ID).isPresent());

        ArgumentCaptor<UpdateItemRequest> requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(requestCaptor.capture());
        assertEquals("critical#11111111-1111-1111-1111-111111111111", requestCaptor.getValue().key().get("periodo").s());
    }

    @Test
    void bloqueiaReprocessamentoQuandoEscritaCondicionalFalha() {
        doThrow(ConditionalCheckFailedException.builder().build())
                .when(dynamoDbClient)
                .updateItem(any(UpdateItemRequest.class));

        assertFalse(gateway.tryStart(FEEDBACK_ID).isPresent());
    }

    @Test
    void tryStartRegistraLeaseComExpiracao() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(null);

        Optional<ProcessingLease> lease = gateway.tryStart(FEEDBACK_ID);

        assertTrue(lease.isPresent());
        ArgumentCaptor<UpdateItemRequest> requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(requestCaptor.capture());
        UpdateItemRequest request = requestCaptor.getValue();
        assertEquals("2026-05-31T13:01:00Z", request.expressionAttributeValues().get(":leaseExpiresAt").s());
        assertTrue(request.conditionExpression().contains("leaseExpiresAt < :now"));
    }

    @Test
    void markAboutToSendExigeOwnerTokenAtual() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(null);
        ProcessingLease lease = new ProcessingLease("owner-1");

        gateway.markAboutToSend(FEEDBACK_ID, lease);

        ArgumentCaptor<UpdateItemRequest> requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(requestCaptor.capture());
        UpdateItemRequest request = requestCaptor.getValue();
        assertEquals("owner-1", request.expressionAttributeValues().get(":ownerToken").s());
        assertEquals("SEND_ATTEMPTED", request.expressionAttributeValues().get(":sendAttempted").s());
    }

    @Test
    void markSentExigeOwnerTokenAtual() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(null);
        ProcessingLease lease = new ProcessingLease("owner-1");

        gateway.markSent(FEEDBACK_ID, lease);

        ArgumentCaptor<UpdateItemRequest> requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().conditionExpression().contains("ownerToken = :ownerToken"));
    }

    @Test
    void executorAntigoNaoAlteraEstadoAdquiridoPorNovoToken() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(null)
                .thenThrow(ConditionalCheckFailedException.builder().build());
        ProcessingLease currentLease = new ProcessingLease("owner-current");
        ProcessingLease staleLease = new ProcessingLease("owner-stale");

        gateway.markSent(FEEDBACK_ID, currentLease);

        assertThrows(
                ConditionalCheckFailedException.class,
                () -> gateway.markSent(FEEDBACK_ID, staleLease));
        verify(dynamoDbClient, times(2)).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void markFailedAfterSendAttemptSoPermiteEstadoSendAttempted() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(null);
        ProcessingLease lease = new ProcessingLease("owner-1");

        gateway.markFailedAfterSendAttempt(FEEDBACK_ID, lease, "timeout");

        ArgumentCaptor<UpdateItemRequest> requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(requestCaptor.capture());
        assertEquals(
                "ownerToken = :ownerToken AND #status = :sendAttempted",
                requestCaptor.getValue().conditionExpression());
    }
}
