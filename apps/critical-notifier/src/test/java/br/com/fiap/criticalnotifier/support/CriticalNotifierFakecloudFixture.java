package br.com.fiap.criticalnotifier.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.ses.SesClient;

public final class CriticalNotifierFakecloudFixture implements AutoCloseable {
    private static final Region REGION = Region.US_EAST_1;

    private final URI endpoint = FakecloudContainerSupport.endpoint();
    private final DynamoDbClient dynamoDbClient;
    private final SesClient sesClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String tableName = "critical-notifier-it-" + UUID.randomUUID();
    private final String sender = "sender-" + UUID.randomUUID() + "@example.com";
    private final String recipient = "recipient-" + UUID.randomUUID() + "@example.com";

    public CriticalNotifierFakecloudFixture() {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        dynamoDbClient = DynamoDbClient.builder()
                .endpointOverride(endpoint)
                .region(REGION)
                .credentialsProvider(credentials)
                .build();
        sesClient = SesClient.builder()
                .endpointOverride(endpoint)
                .region(REGION)
                .credentialsProvider(credentials)
                .build();
        createTable();
        sesClient.verifyEmailIdentity(builder -> builder.emailAddress(sender));
        sesClient.verifyEmailIdentity(builder -> builder.emailAddress(recipient));
    }

    public DynamoDbClient dynamoDbClient() {
        return dynamoDbClient;
    }

    public SesClient sesClient() {
        return sesClient;
    }

    public String tableName() {
        return tableName;
    }

    public String sender() {
        return sender;
    }

    public String recipient() {
        return recipient;
    }

    public void putState(UUID feedbackId, String status, String ownerToken, String leaseExpiresAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("periodo", AttributeValue.fromS(key(feedbackId)));
        item.put("status", AttributeValue.fromS(status));
        if (ownerToken != null) {
            item.put("ownerToken", AttributeValue.fromS(ownerToken));
        }
        if (leaseExpiresAt != null) {
            item.put("leaseExpiresAt", AttributeValue.fromS(leaseExpiresAt));
        }
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
    }

    public Map<String, AttributeValue> state(UUID feedbackId) {
        return dynamoDbClient.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of("periodo", AttributeValue.fromS(key(feedbackId))))
                        .consistentRead(true)
                        .build())
                .item();
    }

    public List<FakeEmail> emailsToRecipient() {
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/_fakecloud/ses/emails")).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "fakecloud SES introspection returned HTTP " + response.statusCode() + ": " + response.body());
            }
            List<FakeEmail> result = new ArrayList<>();
            for (JsonNode email : objectMapper.readTree(response.body()).path("emails")) {
                for (JsonNode address : email.path("to")) {
                    if (recipient.equals(address.asText())) {
                        result.add(new FakeEmail(
                                email.path("from").asText(),
                                address.asText(),
                                email.path("subject").asText(),
                                email.path("textBody").asText()));
                    }
                }
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse fakecloud SES introspection response.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading fakecloud SES introspection response.", exception);
        }
    }

    @Override
    public void close() {
        try {
            dynamoDbClient.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
        } finally {
            try {
                sesClient.deleteIdentity(builder -> builder.identity(sender));
                sesClient.deleteIdentity(builder -> builder.identity(recipient));
            } finally {
                dynamoDbClient.close();
                sesClient.close();
            }
        }
    }

    private void createTable() {
        dynamoDbClient.createTable(CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName("periodo")
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .keySchema(KeySchemaElement.builder()
                        .attributeName("periodo")
                        .keyType(KeyType.HASH)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
    }

    private String key(UUID feedbackId) {
        return "critical#" + feedbackId;
    }

    public record FakeEmail(String from, String to, String subject, String textBody) {
    }
}
