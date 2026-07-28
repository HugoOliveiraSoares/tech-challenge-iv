package br.com.fiap.criticalnotifier.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.RetryPolicyContext;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.model.SesException;

class AwsClientProducerTest {
    @Test
    void configuraSesComTresTentativasETrottlingComoUnicaCondicaoDeRetry() {
        RetryPolicy retryPolicy = AwsClientProducer.sesRetryPolicy();
        var retryCondition = retryPolicy.aggregateRetryCondition();

        assertEquals(2, retryPolicy.numRetries());
        assertFalse(retryPolicy.additionalRetryConditionsAllowed());
        assertTrue(retryCondition.shouldRetry(context(throttlingException(), 0)));
        assertTrue(retryCondition.shouldRetry(context(throttlingException(), 1)));
        assertFalse(retryCondition.shouldRetry(context(throttlingException(), 2)));
        assertFalse(retryCondition.shouldRetry(context(serverException(), 0)));
        assertFalse(retryCondition.shouldRetry(context(transportException(), 0)));
    }

    @Test
    void aplicaPoliticaSomenteAoClienteSes() {
        AwsClientProducer producer = producer();

        try (var sesClient = producer.sesClient();
                var dynamoDbClient = producer.dynamoDbClient();
                var defaultDynamoDbClient = DynamoDbClient.builder().region(Region.US_EAST_1).build()) {
            RetryPolicy sesRetryPolicy = sesClient.serviceClientConfiguration()
                    .overrideConfiguration()
                    .retryPolicy()
                    .orElseThrow();
            RetryPolicy dynamoDbRetryPolicy = dynamoDbClient.serviceClientConfiguration()
                    .overrideConfiguration()
                    .retryPolicy()
                    .orElseThrow();
            RetryPolicy defaultDynamoDbRetryPolicy = defaultDynamoDbClient.serviceClientConfiguration()
                    .overrideConfiguration()
                    .retryPolicy()
                    .orElseThrow();

            assertEquals(2, sesRetryPolicy.numRetries());
            assertEquals(defaultDynamoDbRetryPolicy.numRetries(), dynamoDbRetryPolicy.numRetries());
            assertEquals(defaultDynamoDbRetryPolicy.retryMode(), dynamoDbRetryPolicy.retryMode());
            assertEquals(defaultDynamoDbRetryPolicy.backoffStrategy(), dynamoDbRetryPolicy.backoffStrategy());
            assertEquals(
                    defaultDynamoDbRetryPolicy.throttlingBackoffStrategy(),
                    dynamoDbRetryPolicy.throttlingBackoffStrategy());
        }
    }

    private AwsClientProducer producer() {
        AwsClientProducer producer = new AwsClientProducer();
        producer.awsRegion = "us-east-1";
        producer.endpointUrl = Optional.empty();
        return producer;
    }

    private RetryPolicyContext context(SdkException exception, int retriesAttempted) {
        var builder = RetryPolicyContext.builder()
                .exception(exception)
                .executionAttributes(new ExecutionAttributes())
                .retriesAttempted(retriesAttempted);
        if (exception instanceof SdkServiceException serviceException) {
            builder.httpStatusCode(serviceException.statusCode());
        }
        return builder.build();
    }

    private SesException throttlingException() {
        return (SesException) SesException.builder()
                .statusCode(400)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .serviceName("SES")
                        .errorCode("Throttling")
                        .errorMessage("Maximum sending rate exceeded")
                        .build())
                .build();
    }

    private SesException serverException() {
        return (SesException) SesException.builder()
                .statusCode(503)
                .message("Service unavailable")
                .build();
    }

    private SdkClientException transportException() {
        return SdkClientException.builder().message("Connection reset").build();
    }
}
