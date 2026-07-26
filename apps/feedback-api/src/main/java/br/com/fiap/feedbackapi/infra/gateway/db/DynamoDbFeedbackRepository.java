package br.com.fiap.feedbackapi.infra.gateway.db;

import br.com.fiap.feedbackapi.core.exception.PersistenceException;
import br.com.fiap.feedbackplatform.shared.domain.Feedback;
import br.com.fiap.feedbackplatform.shared.port.FeedbackRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class DynamoDbFeedbackRepository implements FeedbackRepository {

    private static final Logger LOGGER = Logger.getLogger(DynamoDbFeedbackRepository.class);

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbFeedbackRepository(DynamoDbClient dynamoDbClient,
                                      @ConfigProperty(name = "feedback.table-name")
                                      String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public void save(Feedback feedback) {
        Map<String, AttributeValue> item = toItem(feedback);

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        try{
            dynamoDbClient.putItem(request);

            LOGGER.infof("Feedback persistido. feedbackId=%s, correlationId=%s", feedback.id(), feedback.correlationId());

        }catch (SdkException exception){
            LOGGER.errorf(exception,
                    "Falha ao persistir feedback. feedbackId=%s, correlationId=%s", feedback.id(), feedback.correlationId());

            throw new PersistenceException("Falha ao persistir feedback.",
                    exception);
        }
    }

    private Map<String, AttributeValue> toItem(Feedback feedback) {
        Map<String, AttributeValue> item = new HashMap<>();

        item.put("id", AttributeValue.builder()
                .s(feedback.id().toString())
                .build());

        item.put("descricao", AttributeValue.builder()
                .s(feedback.descricao())
                .build());

        item.put("nota", AttributeValue.builder()
                .n(Integer.toString(feedback.nota()))
                .build());

        item.put("urgencia", AttributeValue.builder()
                .s(feedback.urgencia().name())
                .build());

        item.put("dataEnvio", AttributeValue.builder()
                .s(feedback.dataEnvio().toString())
                .build());

        item.put("periodo", AttributeValue.builder()
                .s(feedback.periodo())
                .build());

        item.put("correlationId", AttributeValue.builder()
                .s(feedback.correlationId())
                .build());

        return item;
    }
}
