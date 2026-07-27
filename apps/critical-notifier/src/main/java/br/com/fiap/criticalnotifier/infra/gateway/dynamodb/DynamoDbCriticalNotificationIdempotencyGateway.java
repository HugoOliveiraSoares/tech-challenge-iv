package br.com.fiap.criticalnotifier.infra.gateway.dynamodb;

import br.com.fiap.criticalnotifier.core.domain.ProcessingLease;
import br.com.fiap.criticalnotifier.core.gateway.NotificationIdempotencyGateway;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@ApplicationScoped
public class DynamoDbCriticalNotificationIdempotencyGateway implements NotificationIdempotencyGateway {
    private static final String KEY_PREFIX = "critical#";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SEND_ATTEMPTED = "SEND_ATTEMPTED";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_FAILED_BEFORE_SEND = "FAILED_BEFORE_SEND";
    private static final String STATUS_FAILED_AFTER_SEND_ATTEMPT = "FAILED_AFTER_SEND_ATTEMPT";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final Clock clock;
    private final Duration leaseDuration;

    public DynamoDbCriticalNotificationIdempotencyGateway(
            DynamoDbClient dynamoDbClient,
            @ConfigProperty(name = "PROCESSING_CONTROL_TABLE_NAME") String tableName,
            Clock clock,
            @ConfigProperty(name = "PROCESSING_LEASE_DURATION_SECONDS", defaultValue = "60")
                    int leaseDurationSeconds) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
        this.clock = clock;
        this.leaseDuration = Duration.ofSeconds(leaseDurationSeconds);
    }

    @Override
    public Optional<ProcessingLease> tryStart(UUID feedbackId) {
        ProcessingLease lease = new ProcessingLease(UUID.randomUUID().toString());
        String now = clock.instant().toString();
        String leaseExpiresAt = clock.instant().plus(leaseDuration).toString();

        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("periodo", AttributeValue.fromS(toKey(feedbackId))))
                    .updateExpression(
                            "SET #status = :processing, ownerToken = :ownerToken, leaseExpiresAt = :leaseExpiresAt, "
                                    + "startedAt = :startedAt REMOVE failureReason, failedAt")
                    .conditionExpression(
                            "attribute_not_exists(periodo) OR #status = :failedBeforeSend OR "
                                    + "(#status = :processing AND leaseExpiresAt < :now)")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(
                            ":processing", AttributeValue.fromS(STATUS_PROCESSING),
                            ":failedBeforeSend", AttributeValue.fromS(STATUS_FAILED_BEFORE_SEND),
                            ":ownerToken", AttributeValue.fromS(lease.ownerToken()),
                            ":leaseExpiresAt", AttributeValue.fromS(leaseExpiresAt),
                            ":startedAt", AttributeValue.fromS(now),
                            ":now", AttributeValue.fromS(now)))
                    .build());
            return Optional.of(lease);
        } catch (ConditionalCheckFailedException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void markAboutToSend(UUID feedbackId, ProcessingLease lease) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("periodo", AttributeValue.fromS(toKey(feedbackId))))
                .updateExpression("SET #status = :sendAttempted")
                .conditionExpression("ownerToken = :ownerToken AND #status = :processing")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":sendAttempted", AttributeValue.fromS(STATUS_SEND_ATTEMPTED),
                        ":ownerToken", AttributeValue.fromS(lease.ownerToken()),
                        ":processing", AttributeValue.fromS(STATUS_PROCESSING)))
                .build());
    }

    @Override
    public void markSent(UUID feedbackId, ProcessingLease lease) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("periodo", AttributeValue.fromS(toKey(feedbackId))))
                .updateExpression("SET #status = :status, sentAt = :sentAt")
                .conditionExpression(
                        "ownerToken = :ownerToken AND (#status = :processing OR #status = :sendAttempted)")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.fromS(STATUS_SENT),
                        ":sentAt", AttributeValue.fromS(clock.instant().toString()),
                        ":ownerToken", AttributeValue.fromS(lease.ownerToken()),
                        ":processing", AttributeValue.fromS(STATUS_PROCESSING),
                        ":sendAttempted", AttributeValue.fromS(STATUS_SEND_ATTEMPTED)))
                .build());
    }

    @Override
    public void markFailedBeforeSend(UUID feedbackId, ProcessingLease lease, String reason) {
        markFailed(
                feedbackId,
                lease,
                STATUS_FAILED_BEFORE_SEND,
                reason,
                "ownerToken = :ownerToken AND (#status = :processing OR #status = :sendAttempted)");
    }

    @Override
    public void markFailedAfterSendAttempt(UUID feedbackId, ProcessingLease lease, String reason) {
        markFailed(
                feedbackId,
                lease,
                STATUS_FAILED_AFTER_SEND_ATTEMPT,
                reason,
                "ownerToken = :ownerToken AND #status = :sendAttempted");
    }

    private void markFailed(UUID feedbackId, ProcessingLease lease, String status, String reason, String condition) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":status", AttributeValue.fromS(status));
        values.put(":failedAt", AttributeValue.fromS(clock.instant().toString()));
        values.put(
                ":failureReason",
                AttributeValue.fromS(reason == null || reason.isBlank() ? "Unknown failure" : reason));
        values.put(":ownerToken", AttributeValue.fromS(lease.ownerToken()));
        values.put(":processing", AttributeValue.fromS(STATUS_PROCESSING));
        values.put(":sendAttempted", AttributeValue.fromS(STATUS_SEND_ATTEMPTED));

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("periodo", AttributeValue.fromS(toKey(feedbackId))))
                .updateExpression("SET #status = :status, failedAt = :failedAt, failureReason = :failureReason")
                .conditionExpression(condition)
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(values)
                .build());
    }

    static String toKey(UUID feedbackId) {
        return KEY_PREFIX + feedbackId;
    }
}
