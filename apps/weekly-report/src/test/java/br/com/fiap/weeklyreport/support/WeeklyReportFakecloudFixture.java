package br.com.fiap.weeklyreport.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.VerificationStatus;

public final class WeeklyReportFakecloudFixture implements AutoCloseable {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final URI endpoint = WeeklyReportFakecloud.endpoint();
    private final DynamoDbClient dynamoDb = WeeklyReportFakecloud.dynamoDbClient();
    private final SesClient ses = WeeklyReportFakecloud.sesClient();
    private final String suffix = UUID.randomUUID().toString().replace("-", "");
    private final String feedbackTable = "weekly-feedback-" + suffix;
    private final String processingTable = "weekly-processing-" + suffix;
    private final String sender = "sender-" + suffix + "@example.com";
    private final String recipient = "recipient-" + suffix + "@example.com";

    public WeeklyReportFakecloudFixture() {
        try {
            createFeedbackTable();
            createProcessingTable();
            verifyIdentity(sender);
            verifyIdentity(recipient);
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    public DynamoDbClient dynamoDb() {
        return dynamoDb;
    }

    public SesClient ses() {
        return ses;
    }

    public String feedbackTable() {
        return feedbackTable;
    }

    public String processingTable() {
        return processingTable;
    }

    public String sender() {
        return sender;
    }

    public String recipient() {
        return recipient;
    }

    public void putFeedback(String period, String description, int score, String urgency, String sentAt) {
        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(feedbackTable)
                .item(Map.of(
                        "id", AttributeValue.fromS(UUID.randomUUID().toString()),
                        "periodo", AttributeValue.fromS(period),
                        "descricao", AttributeValue.fromS(description),
                        "nota", AttributeValue.fromN(Integer.toString(score)),
                        "urgencia", AttributeValue.fromS(urgency),
                        "dataEnvio", AttributeValue.fromS(sentAt)))
                .build());
    }

    public Map<String, AttributeValue> processingRecord(String period) {
        return dynamoDb.getItem(builder -> builder
                        .tableName(processingTable)
                        .key(Map.of("periodo", AttributeValue.fromS(period)))
                        .consistentRead(true))
                .item();
    }

    public List<RecordedEmail> emails() {
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/_fakecloud/ses/emails"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "fakecloud SES introspection returned HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode emails = OBJECT_MAPPER.readTree(response.body()).path("emails");
            List<RecordedEmail> recorded = new ArrayList<>();
            for (JsonNode email : emails) {
                List<String> recipients = new ArrayList<>();
                email.path("to").forEach(value -> recipients.add(value.asText()));
                if (recipients.contains(recipient)) {
                    recorded.add(new RecordedEmail(
                            email.path("from").asText(),
                            recipients,
                            email.path("subject").asText(),
                            email.path("textBody").asText()));
                }
            }
            return recorded;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse fakecloud SES introspection response.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading fakecloud SES introspection response.", exception);
        }
    }

    @Override
    public void close() {
        deleteTable(feedbackTable);
        deleteTable(processingTable);
        deleteIdentity(sender);
        deleteIdentity(recipient);
        dynamoDb.close();
        ses.close();
    }

    private void createFeedbackTable() {
        dynamoDb.createTable(CreateTableRequest.builder()
                .tableName(feedbackTable)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        attribute("id"),
                        attribute("periodo"),
                        attribute("dataEnvio"))
                .keySchema(key("id", KeyType.HASH))
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName("dataEnvio-index")
                        .keySchema(key("periodo", KeyType.HASH), key("dataEnvio", KeyType.RANGE))
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build())
                .build());
    }

    private void createProcessingTable() {
        dynamoDb.createTable(CreateTableRequest.builder()
                .tableName(processingTable)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attribute("periodo"))
                .keySchema(key("periodo", KeyType.HASH))
                .build());
    }

    private AttributeDefinition attribute(String name) {
        return AttributeDefinition.builder()
                .attributeName(name)
                .attributeType(ScalarAttributeType.S)
                .build();
    }

    private KeySchemaElement key(String name, KeyType type) {
        return KeySchemaElement.builder().attributeName(name).keyType(type).build();
    }

    private void verifyIdentity(String email) {
        ses.verifyEmailIdentity(builder -> builder.emailAddress(email));
        VerificationStatus status = ses.getIdentityVerificationAttributes(builder -> builder.identities(email))
                .verificationAttributes()
                .get(email)
                .verificationStatus();
        if (status != VerificationStatus.SUCCESS) {
            throw new IllegalStateException("fakecloud did not verify SES identity " + email + ": " + status);
        }
    }

    private void deleteTable(String table) {
        try {
            dynamoDb.deleteTable(builder -> builder.tableName(table));
        } catch (RuntimeException ignored) {
            // Constructor failures may occur before every owned resource exists.
        }
    }

    private void deleteIdentity(String identity) {
        try {
            ses.deleteIdentity(builder -> builder.identity(identity));
        } catch (RuntimeException ignored) {
            // Constructor failures may occur before every owned resource exists.
        }
    }

    public record RecordedEmail(String from, List<String> to, String subject, String textBody) {
    }
}
