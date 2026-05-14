package top.ilovemyhome.zora.poc.logger;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exploration tests for Logback logging framework.
 *
 * <p>Tests demonstrate basic Logback usage via SLF4J API and Logback native API.
 * This is a living document -- add new Logback experiments as needed.</p>
 */
class LoggerFrameworkExploreTest {

    @Test
    void exploreSlf4jWithLogback() {
        Logger logger = LoggerFactory.getLogger(LoggerFrameworkExploreTest.class);
        logger.info("[SLF4J + Logback] Hello from Logback via SLF4J");
        logger.debug("[SLF4J + Logback] Debug message: value = {}", 42);
        logger.warn("[SLF4J + Logback] Warning message: user = {}", "alice");
    }

    @Test
    void exploreLogbackNativeApi() {
        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LoggerFrameworkExploreTest.class);
        logger.info("[Logback Native] Hello from native Logback API");
        logger.debug("[Logback Native] Debug message: value = {}", 42);

        // Logback-specific: dynamically change log level
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        logger.debug("[Logback Native] This debug is visible after level change");
    }
}
