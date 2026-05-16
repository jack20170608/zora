package top.ilovemyhome.zora.poc.logger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates programmatic (dynamic) creation of Logback loggers and appenders.
 *
 * <p>Use cases for dynamic loggers:</p>
 * <ul>
 *   <li>Multi-tenant systems: one log file per tenant/customer.</li>
 *   <li>Plugin architectures: each plugin gets its own logger at runtime.</li>
 *   <li>Job-based processing: each batch job writes to a dedicated log file.</li>
 * </ul>
 *
 * <p>Key APIs used:</p>
 * <ul>
 *   <li>{@link LoggerContext} – the Logback context, obtained from {@code LoggerFactory}.</li>
 *   <li>{@link PatternLayoutEncoder} – programmatic encoder with pattern string.</li>
 *   <li>{@link FileAppender} / {@link RollingFileAppender} – file output destinations.</li>
 *   <li>{@link Logger#addAppender(Appender)} – attach appender to logger.</li>
 * </ul>
 */
class DynamicLoggerCreationTest {

    private static final String LOG_DIR = "target/dynamic-logs";
    private static final String LOG_PATTERN =
        "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";

    private LoggerContext loggerContext;

    @BeforeEach
    void setup() throws IOException {
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Path dir = Paths.get(LOG_DIR);
        if (Files.exists(dir)) {
            Files.list(dir).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } else {
            Files.createDirectories(dir);
        }
    }

    @AfterEach
    void cleanup() throws IOException {
        // Stop and detach dynamic appenders to avoid leaking file handles across tests.
        for (Logger log : loggerContext.getLoggerList()) {
            if (log.getName().startsWith("DYNAMIC.")) {
                java.util.Iterator<Appender<ILoggingEvent>> it = log.iteratorForAppenders();
                while (it.hasNext()) {
                    Appender<ILoggingEvent> appender = it.next();
                    log.detachAppender(appender);
                    appender.stop();
                }
                loggerContext.getLogger(log.getName()).setLevel(null);
            }
        }
    }

    /**
     * Creates a simple FileAppender dynamically and attaches it to a new logger.
     */
    @Test
    void testDynamicFileAppender() throws IOException {
        String loggerName = "DYNAMIC.UserService";
        String logFile = LOG_DIR + "/user-service.log";

        Logger dynamicLogger = createFileLogger(loggerName, logFile, Level.DEBUG);

        dynamicLogger.info("Dynamic logger says hello");
        dynamicLogger.debug("Dynamic debug detail: userId={}", 42);

        // Force flush by stopping the appender
        stopLoggerAppenders(dynamicLogger);

        Path path = Paths.get(logFile);
        assertThat(path).exists();
        List<String> lines = Files.readAllLines(path);
        assertThat(lines).anyMatch(l -> l.contains("Dynamic logger says hello"));
        assertThat(lines).anyMatch(l -> l.contains("userId=42"));
    }

    /**
     * Creates multiple dynamic loggers, each writing to its own file.
     * Simulates a multi-tenant scenario.
     */
    @Test
    void testMultipleDynamicLoggersForMultiTenancy() throws IOException {
        Logger tenantA = createFileLogger("DYNAMIC.TENANT.A", LOG_DIR + "/tenant-a.log", Level.INFO);
        Logger tenantB = createFileLogger("DYNAMIC.TENANT.B", LOG_DIR + "/tenant-b.log", Level.INFO);

        tenantA.info("Tenant A: order placed");
        tenantB.info("Tenant B: payment received");
        tenantA.warn("Tenant A: slow query detected");

        stopLoggerAppenders(tenantA);
        stopLoggerAppenders(tenantB);

        List<String> linesA = Files.readAllLines(Paths.get(LOG_DIR + "/tenant-a.log"));
        List<String> linesB = Files.readAllLines(Paths.get(LOG_DIR + "/tenant-b.log"));

        assertThat(linesA).hasSize(2);
        assertThat(linesA).allMatch(l -> l.contains("Tenant A"));
        assertThat(linesB).hasSize(1);
        assertThat(linesB).allMatch(l -> l.contains("Tenant B"));
    }

    /**
     * Creates a dynamic RollingFileAppender with a TimeBasedRollingPolicy.
     */
    @Test
    void testDynamicRollingFileAppender() throws IOException {
        String loggerName = "DYNAMIC.BatchJob";
        String activeFile = LOG_DIR + "/batch-job.log";
        String rollingPattern = LOG_DIR + "/batch-job.%d{yyyy-MM-dd}.log";

        Logger dynamicLogger = createRollingFileLogger(loggerName, activeFile, rollingPattern, Level.INFO);

        dynamicLogger.info("Batch job started");
        dynamicLogger.info("Batch job processed 1000 records");
        dynamicLogger.info("Batch job completed");

        stopLoggerAppenders(dynamicLogger);

        Path path = Paths.get(activeFile);
        assertThat(path).exists();
        List<String> lines = Files.readAllLines(path);
        assertThat(lines).hasSize(3);
        assertThat(lines).anyMatch(l -> l.contains("Batch job completed"));
    }

    /**
     * Demonstrates adding a second appender to an existing logger at runtime.
     */
    @Test
    void testAddAppenderToExistingLoggerAtRuntime() throws IOException {
        // 1. Obtain an existing logger (could be the class logger)
        Logger existingLogger = (Logger) LoggerFactory.getLogger(DynamicLoggerCreationTest.class);

        // 2. Dynamically create and attach a new file appender
        String extraLogFile = LOG_DIR + "/extra-appender.log";
        FileAppender<ILoggingEvent> extraAppender = createFileAppender("EXTRA", extraLogFile);
        existingLogger.addAppender(extraAppender);

        existingLogger.info("Message written to both original and extra appender");

        // 3. Clean up: detach and stop the extra appender so other tests are not affected
        existingLogger.detachAppender(extraAppender);
        extraAppender.stop();

        Path path = Paths.get(extraLogFile);
        assertThat(path).exists();
        List<String> lines = Files.readAllLines(path);
        assertThat(lines).anyMatch(l -> l.contains("Message written to both original and extra appender"));
    }

    // ========================= Helper Methods =========================

    /**
     * Creates a logger with a single FileAppender.
     *
     * @param loggerName the SLF4J/Logback logger name
     * @param filePath   the absolute or relative log file path
     * @param level      the log level for this logger
     * @return the configured Logger instance
     */
    private Logger createFileLogger(String loggerName, String filePath, Level level) {
        Logger logger = loggerContext.getLogger(loggerName);
        logger.setLevel(level);
        logger.setAdditive(false);

        FileAppender<ILoggingEvent> appender = createFileAppender(loggerName + "-FILE", filePath);
        logger.addAppender(appender);
        return logger;
    }

    /**
     * Creates a logger with a RollingFileAppender.
     *
     * @param loggerName      the logger name
     * @param activeFile      the currently active log file
     * @param rollingPattern  the file name pattern for rolled-over archives
     * @param level           the log level
     * @return the configured Logger instance
     */
    private Logger createRollingFileLogger(String loggerName, String activeFile,
                                           String rollingPattern, Level level) {
        Logger logger = loggerContext.getLogger(loggerName);
        logger.setLevel(level);
        logger.setAdditive(false);

        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(loggerContext);
        appender.setName(loggerName + "-ROLLING");
        appender.setFile(activeFile);

        TimeBasedRollingPolicy<ILoggingEvent> policy = new TimeBasedRollingPolicy<>();
        policy.setContext(loggerContext);
        policy.setParent(appender);
        policy.setFileNamePattern(rollingPattern);
        policy.setMaxHistory(7);
        policy.start();

        appender.setRollingPolicy(policy);
        appender.setEncoder(createEncoder());
        appender.start();

        logger.addAppender(appender);
        return logger;
    }

    private FileAppender<ILoggingEvent> createFileAppender(String name, String filePath) {
        FileAppender<ILoggingEvent> appender = new FileAppender<>();
        appender.setContext(loggerContext);
        appender.setName(name);
        appender.setFile(filePath);
        appender.setEncoder(createEncoder());
        appender.start();
        return appender;
    }

    private PatternLayoutEncoder createEncoder() {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern(LOG_PATTERN);
        encoder.start();
        return encoder;
    }

    private void stopLoggerAppenders(Logger logger) {
        java.util.Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders();
        while (it.hasNext()) {
            it.next().stop();
        }
    }
}
