package top.ilovemyhome.zora.poc.logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates Logback {@code SiftingAppender} for MDC-based dynamic file routing.
 *
 * <p><b>SiftingAppender</b> is Logback's built-in solution for creating log files
 * dynamically based on a runtime context value (typically an MDC key). Compared to
 * manual programmatic creation, it is:</p>
 * <ul>
 *   <li><b>Declarative</b> – configuration lives in XML/Groovy, not Java code.</li>
 *   <li><b>Self-managing</b> – appenders are created on first use and cleaned up
 *       automatically when the MDC value changes or the context ends.</li>
 *   <li><b>Less error-prone</b> – no manual {@code start()}/{@code stop()} lifecycle.</li>
 * </ul>
 *
 * <p>Typical use cases:</p>
 * <ul>
 *   <li>Multi-tenant SaaS: one log file per {@code tenantId}.</li>
 *   <li>Request tracing: isolate logs per {@code requestId} or {@code traceId}.</li>
 *   <li>Batch jobs: separate files per {@code jobId}.</li>
 * </ul>
 */
class SiftingAppenderTest {

    private static final Logger LOGGER =
        LoggerFactory.getLogger("top.ilovemyhome.zora.poc.logger.sifting");

    private static final Path LOG_DIR = Paths.get("target/logs");

    @BeforeEach
    void cleanLogDirectory() throws IOException {
        if (Files.exists(LOG_DIR)) {
            Files.list(LOG_DIR)
                .filter(p -> p.getFileName().toString().startsWith("tenant-"))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
        }
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void testSiftingByTenantId() throws IOException {
        // Tenant ACME
        MDC.put("tenantId", "acme");
        LOGGER.info("Tenant ACME: order created");
        LOGGER.info("Tenant ACME: payment processed");

        // Tenant GLOBEX
        MDC.put("tenantId", "globex");
        LOGGER.info("Tenant GLOBEX: user registered");

        // Tenant with no MDC set -> fallback to 'unknown'
        MDC.clear();
        LOGGER.info("Anonymous request received");

        Path acmeLog = LOG_DIR.resolve("tenant-acme.log");
        Path globexLog = LOG_DIR.resolve("tenant-globex.log");
        Path unknownLog = LOG_DIR.resolve("tenant-unknown.log");

        assertThat(acmeLog).exists();
        assertThat(globexLog).exists();
        assertThat(unknownLog).exists();

        List<String> acmeLines = Files.readAllLines(acmeLog);
        List<String> globexLines = Files.readAllLines(globexLog);
        List<String> unknownLines = Files.readAllLines(unknownLog);

        assertThat(acmeLines).hasSize(2);
        assertThat(acmeLines).allMatch(l -> l.contains("Tenant ACME"));

        assertThat(globexLines).hasSize(1);
        assertThat(globexLines).allMatch(l -> l.contains("Tenant GLOBEX"));

        assertThat(unknownLines).hasSize(1);
        assertThat(unknownLines).anyMatch(l -> l.contains("Anonymous request"));
    }

    @Test
    void testSameCodePathDifferentMdcProducesDifferentFiles() throws IOException {
        // Simulate a reusable service method called on behalf of different tenants
        processOrder("tenant-alfa", "ORDER-001");
        processOrder("tenant-beta", "ORDER-002");
        processOrder("tenant-alfa", "ORDER-003");

        Path alfaLog = LOG_DIR.resolve("tenant-tenant-alfa.log");
        Path betaLog = LOG_DIR.resolve("tenant-tenant-beta.log");

        assertThat(alfaLog).exists();
        assertThat(betaLog).exists();

        List<String> alfaLines = Files.readAllLines(alfaLog);
        List<String> betaLines = Files.readAllLines(betaLog);

        assertThat(alfaLines).hasSize(2);
        assertThat(alfaLines).anyMatch(l -> l.contains("ORDER-001"));
        assertThat(alfaLines).anyMatch(l -> l.contains("ORDER-003"));

        assertThat(betaLines).hasSize(1);
        assertThat(betaLines).anyMatch(l -> l.contains("ORDER-002"));
    }

    private void processOrder(String tenantId, String orderId) {
        MDC.put("tenantId", tenantId);
        try {
            LOGGER.info("Processing order {}", orderId);
        } finally {
            MDC.clear();
        }
    }
}
