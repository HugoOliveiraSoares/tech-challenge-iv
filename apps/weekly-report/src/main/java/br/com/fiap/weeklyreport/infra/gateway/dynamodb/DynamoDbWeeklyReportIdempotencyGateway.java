package br.com.fiap.weeklyreport.infra.gateway.dynamodb;

import br.com.fiap.weeklyreport.core.gateway.WeeklyReportIdempotencyGateway;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@ApplicationScoped
public class DynamoDbWeeklyReportIdempotencyGateway implements WeeklyReportIdempotencyGateway {
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final Clock clock;

    public DynamoDbWeeklyReportIdempotencyGateway(
            DynamoDbClient dynamoDbClient,
            @ConfigProperty(name = "PROCESSING_CONTROL_TABLE_NAME") String tableName,
            Clock clock) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
        this.clock = clock;
    }

    @Override
    public boolean tryStart(String periodo) {
        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("periodo", AttributeValue.fromS(periodo)))
                    .updateExpression("SET #status = :processing, startedAt = :startedAt REMOVE failureReason, failedAt")
                    .conditionExpression("attribute_not_exists(periodo) OR #status = :failed")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(
                            ":processing", AttributeValue.fromS("PROCESSING"),
                            ":failed", AttributeValue.fromS("FAILED"),
                            ":startedAt", AttributeValue.fromS(clock.instant().toString())))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
    }

    @Override
    public void markSent(String periodo) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("periodo", AttributeValue.fromS(periodo)))
                .updateExpression("SET #status = :status, sentAt = :sentAt")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.fromS("SENT"),
                        ":sentAt", AttributeValue.fromS(clock.instant().toString())))
                .build());
    }

    @Override
    public void markFailed(String periodo, String reason) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("periodo", AttributeValue.fromS(periodo)))
                .updateExpression("SET #status = :status, failedAt = :failedAt, failureReason = :failureReason")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.fromS("FAILED"),
                        ":failedAt", AttributeValue.fromS(clock.instant().toString()),
                        ":failureReason", AttributeValue.fromS(reason == null || reason.isBlank() ? "Unknown failure" : reason)))
                .build());
    }
}
