package br.com.fiap.weeklyreport.infra.gateway.dynamodb;

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
class DynamoDbWeeklyReportIdempotencyGatewayTest {
    private static final String PERIOD = "2026-W26";
    private static final String NOW = "2026-06-24T10:00:00Z";

    @Mock
    DynamoDbClient dynamoDbClient;

    private DynamoDbWeeklyReportIdempotencyGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new DynamoDbWeeklyReportIdempotencyGateway(
                dynamoDbClient,
                "processing-test",
                Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC));
    }

    @Test
    void acquiresNewOrRetryablePeriodWithTimestamp() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(null);

        assertTrue(gateway.tryStart(PERIOD));

        UpdateItemRequest request = captureRequest();
        assertEquals(PERIOD, request.key().get("periodo").s());
        assertEquals("PROCESSING", request.expressionAttributeValues().get(":processing").s());
        assertEquals("FAILED_BEFORE_SEND", request.expressionAttributeValues().get(":failedBeforeSend").s());
        assertEquals(NOW, request.expressionAttributeValues().get(":startedAt").s());
        assertEquals("attribute_not_exists(periodo) OR #status = :failedBeforeSend", request.conditionExpression());
        assertTrue(request.updateExpression().contains("REMOVE failureReason, failedAt"));
    }

    @Test
    void conditionalRejectionReturnsFalse() {
        doThrow(ConditionalCheckFailedException.builder().build())
                .when(dynamoDbClient).updateItem(any(UpdateItemRequest.class));

        assertFalse(gateway.tryStart(PERIOD));
    }

    @Test
    void recordsSentStateAndTimestamp() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(null);

        gateway.markSent(PERIOD);

        UpdateItemRequest request = captureRequest();
        assertEquals("SENT", request.expressionAttributeValues().get(":status").s());
        assertEquals(NOW, request.expressionAttributeValues().get(":sentAt").s());
    }

    @Test
    void recordsRetryableFailureBeforeSendAndReason() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(null);

        gateway.markFailedBeforeSend(PERIOD, "DynamoDB unavailable");

        UpdateItemRequest request = captureRequest();
        assertEquals("FAILED_BEFORE_SEND", request.expressionAttributeValues().get(":status").s());
        assertEquals("DynamoDB unavailable", request.expressionAttributeValues().get(":failureReason").s());
        assertEquals(NOW, request.expressionAttributeValues().get(":failedAt").s());
    }

    @Test
    void recordsAmbiguousFailureAfterSendAndNormalizesBlankReason() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(null);

        gateway.markFailedAfterSendAttempt(PERIOD, "  ");

        UpdateItemRequest request = captureRequest();
        assertEquals("FAILED_AFTER_SEND_ATTEMPT", request.expressionAttributeValues().get(":status").s());
        assertEquals("Unknown failure", request.expressionAttributeValues().get(":failureReason").s());
        assertEquals(NOW, request.expressionAttributeValues().get(":failedAt").s());
    }

    private UpdateItemRequest captureRequest() {
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());
        return captor.getValue();
    }
}
