package top.ilovemyhome.zora.poc.logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests demonstrating logging to multiple files with Logback.
 *
 * <p>This test class showcases several common patterns:</p>
 * <ul>
 *   <li><b>Pattern A</b>: Dedicated logger name routes to a dedicated file (audit.log).</li>
 *   <li><b>Pattern B</b>: Different loggers write to different files based on package/name.</li>
 *   <li><b>Pattern C</b>: Same event is written to multiple files (application + rolling).</li>
 *   <li><b>Pattern D</b>: Level-based filtering routes ERROR messages to a separate file.</li>
 * </ul>
 */
class MultipleFileLoggingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultipleFileLoggingTest.class);
    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("AUDIT");
    private static final Logger SERVICE_LOGGER =
        LoggerFactory.getLogger("top.ilovemyhome.zora.poc.logger.service.OrderService");

    private static final Path LOG_DIR = Paths.get("target/logs");

    @BeforeEach
    void cleanLogDirectory() throws IOException {
        if (Files.exists(LOG_DIR)) {
            Files.list(LOG_DIR).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // ignore
                }
            });
        }
    }

    @AfterEach
    void printLogContents() throws IOException {
        if (!Files.exists(LOG_DIR)) {
            return;
        }
        System.out.println("\n========== Log File Contents ==========");
        Files.list(LOG_DIR)
            .filter(Files::isRegularFile)
            .sorted()
            .forEach(p -> {
                System.out.println("\n--- " + p.getFileName() + " ---");
                try {
                    Files.readAllLines(p).forEach(System.out::println);
                } catch (IOException e) {
                    System.out.println("(unable to read)");
                }
            });
        System.out.println("=======================================\n");
    }

    @Test
    void testDedicatedAuditLogger() throws IOException {
        AUDIT_LOGGER.info("User alice logged in");
        AUDIT_LOGGER.info("User alice performed payment action");

        Path auditLog = LOG_DIR.resolve("audit.log");
        assertThat(auditLog).exists();

        List<String> lines = Files.readAllLines(auditLog);
        assertThat(lines).hasSize(2);
        assertThat(lines).allMatch(l -> l.contains("[AUDIT]"));
        assertThat(lines).anyMatch(l -> l.contains("User alice logged in"));
    }

    @Test
    void testServiceLoggerWritesToApplicationAndRolling() throws IOException {
        SERVICE_LOGGER.debug("OrderService: fetching order by id=123");
        SERVICE_LOGGER.info("OrderService: order fetched successfully");

        Path appLog = LOG_DIR.resolve("application.log");
        Path rollingLog = LOG_DIR.resolve("rolling.log");

        assertThat(appLog).exists();
        assertThat(rollingLog).exists();

        List<String> appLines = Files.readAllLines(appLog);
        assertThat(appLines).anyMatch(l -> l.contains("OrderService: order fetched successfully"));

        List<String> rollingLines = Files.readAllLines(rollingLog);
        assertThat(rollingLines).anyMatch(l -> l.contains("OrderService: order fetched successfully"));
    }

    @Test
    void testErrorLevelFilteredToSeparateFile() throws IOException {
        LOGGER.info("This is a normal info message");
        LOGGER.error("This is a critical error");
        LOGGER.warn("This is a warning");
        LOGGER.error("Another critical error occurred");

        Path errorLog = LOG_DIR.resolve("error.log");
        assertThat(errorLog).exists();

        List<String> errorLines = Files.readAllLines(errorLog);
        assertThat(errorLines).hasSize(2);
        assertThat(errorLines).allMatch(l -> l.contains("ERROR"));
        assertThat(errorLines).anyMatch(l -> l.contains("critical error"));
    }

    @Test
    void testMultipleLoggersWriteSimultaneously() throws IOException {
        LOGGER.info("Main logger: system startup");
        SERVICE_LOGGER.info("Service logger: initializing connection pool");
        AUDIT_LOGGER.info("Audit logger: admin accessed settings");
        LOGGER.error("Main logger: database connection failed");

        Path appLog = LOG_DIR.resolve("application.log");
        Path errorLog = LOG_DIR.resolve("error.log");
        Path auditLog = LOG_DIR.resolve("audit.log");
        Path rollingLog = LOG_DIR.resolve("rolling.log");

        // application.log receives SERVICE_LOGGER (additivity=false) and anything
        // explicitly wired to APP_FILE. In our config only SERVICE_LOGGER -> APP_FILE.
        assertThat(appLog).exists();
        List<String> appLines = Files.readAllLines(appLog);
        assertThat(appLines).anyMatch(l -> l.contains("initializing connection pool"));

        // error.log receives only ERROR level from root logger
        assertThat(errorLog).exists();
        List<String> errorLines = Files.readAllLines(errorLog);
        assertThat(errorLines).anyMatch(l -> l.contains("database connection failed"));
        assertThat(errorLines).noneMatch(l -> l.contains("system startup"));

        // audit.log receives only AUDIT_LOGGER
        assertThat(auditLog).exists();
        List<String> auditLines = Files.readAllLines(auditLog);
        assertThat(auditLines).anyMatch(l -> l.contains("admin accessed settings"));
        assertThat(auditLines).noneMatch(l -> l.contains("system startup"));

        // rolling.log receives root logger + SERVICE_LOGGER
        assertThat(rollingLog).exists();
        List<String> rollingLines = Files.readAllLines(rollingLog);
        assertThat(rollingLines).anyMatch(l -> l.contains("system startup"));
        assertThat(rollingLines).anyMatch(l -> l.contains("initializing connection pool"));
    }
}
