package br.com.fiap.criticalnotifier.infra.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;

@ApplicationScoped
public class AwsClientProducer {
    @ConfigProperty(name = "AWS_REGION", defaultValue = "us-east-1")
    String awsRegion;

    @ConfigProperty(name = "AWS_ENDPOINT_URL")
    Optional<String> endpointUrl;

    @Produces
    @ApplicationScoped
    DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder().region(Region.of(awsRegion));
        endpointUrl.filter(endpoint -> !endpoint.isBlank()).map(URI::create).ifPresent(builder::endpointOverride);
        return builder.build();
    }

    @Produces
    @ApplicationScoped
    SesClient sesClient() {
        var builder = SesClient.builder()
                .region(Region.of(awsRegion))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryPolicy(sesRetryPolicy())
                        .build());
        endpointUrl.filter(endpoint -> !endpoint.isBlank()).map(URI::create).ifPresent(builder::endpointOverride);
        return builder.build();
    }

    static RetryPolicy sesRetryPolicy() {
        var backoffStrategy = FullJitterBackoffStrategy.builder()
                .baseDelay(Duration.ofMillis(100))
                .maxBackoffTime(Duration.ofSeconds(20))
                .build();
        return RetryPolicy.builder()
                .numRetries(2)
                .retryCondition(context -> context.exception() instanceof AwsServiceException exception
                        && exception.isThrottlingException())
                .additionalRetryConditionsAllowed(false)
                .backoffStrategy(backoffStrategy)
                .throttlingBackoffStrategy(backoffStrategy)
                .build();
    }
}
