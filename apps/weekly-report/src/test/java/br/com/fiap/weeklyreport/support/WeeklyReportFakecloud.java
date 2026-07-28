package br.com.fiap.weeklyreport.support;

import java.net.URI;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;

public final class WeeklyReportFakecloud {
    public static final Region REGION = Region.US_EAST_1;
    private static final int PORT = 4566;

    private static GenericContainer<?> container;

    private WeeklyReportFakecloud() {
    }

    public static synchronized URI endpoint() {
        startIfNeeded();
        return URI.create("http://" + container.getHost() + ":" + container.getMappedPort(PORT));
    }

    public static DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(endpoint())
                .region(REGION)
                .credentialsProvider(credentials())
                .build();
    }

    public static SesClient sesClient() {
        return SesClient.builder()
                .endpointOverride(endpoint())
                .region(REGION)
                .credentialsProvider(credentials())
                .build();
    }

    @SuppressWarnings("resource")
    private static void startIfNeeded() {
        if (container != null && container.isRunning()) {
            return;
        }

        String image = System.getProperty("fakecloud.image");
        if (image == null || image.isBlank() || image.startsWith("${")) {
            throw new IllegalStateException(
                    "The fakecloud.image Maven property must contain the repository's pinned fakecloud image.");
        }

        GenericContainer<?> candidate = new GenericContainer<>(DockerImageName.parse(image))
                .withExposedPorts(PORT)
                .waitingFor(Wait.forHttp("/_fakecloud/health")
                        .forPort(PORT)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        try {
            candidate.start();
            container = candidate;
        } catch (RuntimeException exception) {
            candidate.close();
            throw new IllegalStateException(
                    "Unable to start fakecloud with Testcontainers. Ensure Docker is installed, running, "
                            + "and can pull " + image + ".",
                    exception);
        }
    }

    private static StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
    }
}
