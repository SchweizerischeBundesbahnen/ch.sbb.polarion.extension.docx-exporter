package ch.sbb.polarion.extension.docx_exporter.pandoc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

/**
 * Singleton container holder for Pandoc service.
 * Uses proper lazy initialization with thread safety (Bill Pugh pattern).
 * The container is shared across all integration tests to avoid startup overhead.
 */
public final class SharedPandocContainer {

    private static final Logger logger = LoggerFactory.getLogger(SharedPandocContainer.class);
    private static final String DOCKER_IMAGE_NAME = "ghcr.io/schweizerischebundesbahnen/pandoc-service:latest";
    private static final int PANDOC_PORT = 9082;

    private SharedPandocContainer() {
        // Private constructor to prevent instantiation
    }

    /**
     * Bill Pugh Singleton Implementation using inner static helper class.
     * Thread-safe without synchronization overhead.
     */
    private static class ContainerHolder {
        private static final GenericContainer<?> INSTANCE = createAndStartContainer();

        private static GenericContainer<?> createAndStartContainer() {
            try {
                logger.info("Starting shared Pandoc container...");

                GenericContainer<?> container = new GenericContainer<>(DOCKER_IMAGE_NAME)
                        .withExposedPorts(PANDOC_PORT)
                        .waitingFor(
                                Wait.forHttp("/version").forPort(PANDOC_PORT)
                                        .forStatusCode(200)
                                        .withStartupTimeout(Duration.ofMinutes(2))
                        );

                container.start();

                if (!container.isRunning()) {
                    throw new IllegalStateException("Container failed to start");
                }

                logger.info("Shared Pandoc container started successfully on port {}",
                        container.getMappedPort(PANDOC_PORT));

                return container;
            } catch (Exception e) {
                logger.error("Failed to start Pandoc container", e);
                throw new RuntimeException("Failed to start Pandoc container", e);
            }
        }
    }

    /**
     * Get the shared container instance.
     * Container is started on first access (lazy initialization).
     *
     * @return the shared Pandoc container
     */
    public static GenericContainer<?> getInstance() {
        return ContainerHolder.INSTANCE;
    }

    /**
     * Get the base URL for the Pandoc service.
     *
     * @return the base URL (e.g., "http://localhost:32768")
     */
    public static String getBaseUrl() {
        GenericContainer<?> container = getInstance();
        return "http://" + container.getHost() + ":" + container.getMappedPort(PANDOC_PORT);
    }

    /**
     * Get the mapped port for the Pandoc service.
     *
     * @return the mapped port number
     */
    public static int getMappedPort() {
        return getInstance().getMappedPort(PANDOC_PORT);
    }

    /**
     * Explicitly stop the container if needed.
     * Note: TestContainers will automatically clean up containers when JVM exits.
     */
    public static void stopIfRunning() {
        try {
            GenericContainer<?> container = ContainerHolder.INSTANCE;
            if (container.isRunning()) {
                logger.info("Stopping shared Pandoc container...");
                container.stop();
            }
        } catch (Exception e) {
            logger.warn("Error stopping container: {}", e.getMessage());
        }
    }
}
