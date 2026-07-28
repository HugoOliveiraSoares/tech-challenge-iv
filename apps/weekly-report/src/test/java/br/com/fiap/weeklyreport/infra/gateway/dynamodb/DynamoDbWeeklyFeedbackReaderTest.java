package br.com.fiap.weeklyreport.infra.gateway.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.feedbackplatform.shared.domain.Urgencia;
import br.com.fiap.weeklyreport.core.domain.WeeklyFeedback;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

@ExtendWith(MockitoExtension.class)
class DynamoDbWeeklyFeedbackReaderTest {
    private static final String TABLE = "feedbacks-test";
    private static final String PERIOD = "2026-W26";

    @Mock
    DynamoDbClient dynamoDbClient;

    private DynamoDbWeeklyFeedbackReader reader;

    @BeforeEach
    void setUp() {
        reader = new DynamoDbWeeklyFeedbackReader(dynamoDbClient, TABLE);
    }

    @Test
    void buildsProductionCompatibleQueryAndMapsItems() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(List.of(item(id, "Atendimento demorado", 2, "CRITICA", "2026-06-22T10:00:00Z")))
                .build());

        List<WeeklyFeedback> result = reader.findByPeriodo(PERIOD);

        ArgumentCaptor<QueryRequest> request = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(request.capture());
        assertEquals(TABLE, request.getValue().tableName());
        assertEquals("dataEnvio-index", request.getValue().indexName());
        assertEquals("periodo = :periodo", request.getValue().keyConditionExpression());
        assertEquals(PERIOD, request.getValue().expressionAttributeValues().get(":periodo").s());
        assertEquals(1, result.size());
        assertEquals(id, result.getFirst().id());
        assertEquals("Atendimento demorado", result.getFirst().descricao());
        assertEquals(2, result.getFirst().nota());
        assertEquals(Urgencia.CRITICA, result.getFirst().urgencia());
        assertEquals(Instant.parse("2026-06-22T10:00:00Z"), result.getFirst().dataEnvio());
    }

    @Test
    void returnsEmptyListForEmptyResponse() {
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().items(List.of()).build());

        assertTrue(reader.findByPeriodo(PERIOD).isEmpty());
    }

    @Test
    void followsLastEvaluatedKeyAndAggregatesAllPages() {
        Map<String, AttributeValue> cursor = Map.of("id", AttributeValue.fromS("page-1"));
        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder()
                        .items(List.of(item(UUID.randomUUID(), "Primeiro feedback", 8, "BAIXA", "2026-06-22T10:00:00Z")))
                        .lastEvaluatedKey(cursor)
                        .build())
                .thenReturn(QueryResponse.builder()
                        .items(List.of(item(UUID.randomUUID(), "Segundo feedback", 5, "MEDIA", "2026-06-23T10:00:00Z")))
                        .build());

        List<WeeklyFeedback> result = reader.findByPeriodo(PERIOD);

        ArgumentCaptor<QueryRequest> requests = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient, times(2)).query(requests.capture());
        assertEquals(2, result.size());
        assertEquals(PERIOD, requests.getAllValues().get(1).expressionAttributeValues().get(":periodo").s());
        assertEquals(cursor, requests.getAllValues().get(1).exclusiveStartKey());
    }

    private Map<String, AttributeValue> item(UUID id, String description, int score, String urgency, String sentAt) {
        return Map.of(
                "id", AttributeValue.fromS(id.toString()),
                "descricao", AttributeValue.fromS(description),
                "nota", AttributeValue.fromN(Integer.toString(score)),
                "urgencia", AttributeValue.fromS(urgency),
                "dataEnvio", AttributeValue.fromS(sentAt));
    }
}
