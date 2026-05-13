package top.ilovemyhome.zora.poc.logger;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.logging.Logger;

/**
 * Exploration tests for various Java logging frameworks.
 *
 * <p>Each test demonstrates the basic usage of a specific logging framework.
 * This is a living document -- add new frameworks or experiments as needed.</p>
 */
class LoggerFrameworkExploreTest {

    @Test
    void exploreJavaUtilLogging() {
        Logger logger = Logger.getLogger(LoggerFrameworkExploreTest.class.getName());
        logger.info("[JUL] Hello from java.util.logging");
        logger.warning("[JUL] This is a warning");
    }

    @Test
    void exploreSlf4jWithSimple() {
        org.slf4j.Logger logger = LoggerFactory.getLogger(LoggerFrameworkExploreTest.class);
        logger.info("[SLF4J] Hello from SLF4J + slf4j-simple");
        logger.debug("[SLF4J] Debug message: value = {}", 42);
    }

    @Test
    void exploreLog4j2() {
        org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(LoggerFrameworkExploreTest.class);
        logger.info("[Log4j2] Hello from Apache Log4j 2");
        logger.debug("[Log4j2] Debug message: value = {}", 42);
    }

    @Test
    void exploreTinylog() {
        org.tinylog.Logger.info("[tinylog] Hello from tinylog");
        org.tinylog.Logger.debug("[tinylog] Debug message: value = {}", 42);
    }

    @Test
    void exploreJbossLogging() {
        org.jboss.logging.Logger logger = org.jboss.logging.Logger.getLogger(LoggerFrameworkExploreTest.class);
        logger.infof("[JBoss Logging] Hello from JBoss Logging");
        logger.debugf("[JBoss Logging] Debug message: value = %d", 42);
    }

    @Test
    void exploreGoogleFlogger() {
        com.google.common.flogger.FluentLogger logger = com.google.common.flogger.FluentLogger.forEnclosingClass();
        logger.atInfo().log("[Flogger] Hello from Google Flogger");
        logger.atFine().log("[Flogger] Fine message: value = %d", 42);
    }
}
