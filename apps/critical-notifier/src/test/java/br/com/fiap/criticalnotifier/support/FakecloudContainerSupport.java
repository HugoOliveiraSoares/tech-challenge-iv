package br.com.fiap.criticalnotifier.support;

import java.net.URI;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

final class FakecloudContainerSupport {
    private static final int PORT = 4566;
    private static final String DEFAULT_IMAGE = "ghcr.io/faiscadev/fakecloud:0.44.6";

    private static GenericContainer<?> container;

    private FakecloudContainerSupport() {
    }

    static synchronized URI endpoint() {
        if (container == null) {
            String image = System.getProperty("fakecloud.image", DEFAULT_IMAGE);
            container = new GenericContainer<>(DockerImageName.parse(image))
                    .withExposedPorts(PORT)
                    .waitingFor(Wait.forHttp("/_fakecloud/health")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(60)));
            try {
                container.start();
            } catch (RuntimeException | AssertionError exception) {
                container = null;
                throw new IllegalStateException(
                        "Unable to start fakecloud with Testcontainers. Ensure Docker is running with a compatible "
                                + "API and image '" + image
                                + "' is pullable; override it with -Dfakecloud.image=<image>.",
                        exception);
            }
        }
        return URI.create("http://" + container.getHost() + ":" + container.getMappedPort(PORT));
    }
}
