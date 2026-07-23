package br.com.fiap.weeklyreport.infra.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
        var builder = SesClient.builder().region(Region.of(awsRegion));
        endpointUrl.filter(endpoint -> !endpoint.isBlank()).map(URI::create).ifPresent(builder::endpointOverride);
        return builder.build();
    }
}
