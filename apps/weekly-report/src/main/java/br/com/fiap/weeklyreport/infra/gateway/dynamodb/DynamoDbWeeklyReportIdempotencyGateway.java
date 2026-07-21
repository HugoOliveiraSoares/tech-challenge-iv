package br.com.fiap.weeklyreport.infra.gateway.dynamodb;

import br.com.fiap.weeklyreport.core.gateway.WeeklyReportIdempotencyGateway;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
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
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            "periodo", AttributeValue.fromS(periodo),
                            "status", AttributeValue.fromS("PROCESSING"),
                            "startedAt", AttributeValue.fromS(clock.instant().toString())))
                    .conditionExpression("attribute_not_exists(periodo)")
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
}
