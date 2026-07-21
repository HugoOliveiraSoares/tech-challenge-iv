package br.com.fiap.weeklyreport.infra.gateway.dynamodb;

import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import br.com.fiap.weeklyreport.core.domain.WeeklyFeedback;
import br.com.fiap.weeklyreport.core.gateway.WeeklyFeedbackReader;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

@ApplicationScoped
public class DynamoDbWeeklyFeedbackReader implements WeeklyFeedbackReader {
    private static final Logger LOGGER = Logger.getLogger(DynamoDbWeeklyFeedbackReader.class);
    private static final String DATA_ENVIO_INDEX = "dataEnvio-index";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbWeeklyFeedbackReader(
            DynamoDbClient dynamoDbClient,
            @ConfigProperty(name = "FEEDBACK_TABLE_NAME") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public List<WeeklyFeedback> findByPeriodo(String periodo) {
        try {
            List<WeeklyFeedback> feedbacks = new ArrayList<>();
            Map<String, AttributeValue> lastEvaluatedKey = null;
            do {
                QueryRequest request = QueryRequest.builder()
                        .tableName(tableName)
                        .indexName(DATA_ENVIO_INDEX)
                        .keyConditionExpression("periodo = :periodo")
                        .expressionAttributeValues(Map.of(":periodo", AttributeValue.fromS(periodo)))
                        .exclusiveStartKey(lastEvaluatedKey)
                        .build();

                var response = dynamoDbClient.query(request);
                response.items().stream().map(this::toFeedback).forEach(feedbacks::add);
                lastEvaluatedKey = response.lastEvaluatedKey();
            } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

            return feedbacks;
        } catch (RuntimeException exception) {
            LOGGER.errorf(exception, "Failed to query weekly feedbacks. periodo=%s", periodo);
            throw exception;
        }
    }

    private WeeklyFeedback toFeedback(Map<String, AttributeValue> item) {
        return new WeeklyFeedback(
                UUID.fromString(item.get("id").s()),
                item.get("descricao").s(),
                Integer.parseInt(item.get("nota").n()),
                Urgencia.valueOf(item.get("urgencia").s()),
                Instant.parse(item.get("dataEnvio").s()));
    }
}
