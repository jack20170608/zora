package top.ilovemyhome.zora.poc.logger.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.poc.logger.common.server.MockHttpServer;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link HttpAppender} using {@link MockHttpServer}.
 *
 * <p>Demonstrates an end-to-end flow:</p>
 * <ol>
 *   <li>Start a mock HTTP server that collects incoming POST bodies.</li>
 *   <li>Create a Logback logger and attach a custom {@link HttpAppender}.</li>
 *   <li>Write log messages through SLF4J.</li>
 *   <li>Assert that the mock server received the serialized log events.</li>
 * </ol>
 */
class HttpAppenderTest {

    @Test
    void testHttpAppenderSendsLogsToRemoteEndpoint() throws Exception {
        List<String> receivedBodies = new CopyOnWriteArrayList<>();

        try (MockHttpServer server = MockHttpServer.create(0)) {
            server.registerHandler("/api/log", exchange -> {
                try (InputStream is = exchange.getRequestBody()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    receivedBodies.add(body);
                }
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            server.start();

            // Build encoder
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
            encoder.start();

            // Build and attach HttpAppender
            HttpAppender appender = new HttpAppender();
            appender.setContext(context);
            appender.setName("HTTP");
            appender.setEndpoint(server.baseUri().resolve("api/log").toString());
            appender.setEncoder(encoder);
            appender.start();

            Logger logger = context.getLogger(HttpAppenderTest.class);
            logger.setLevel(Level.INFO);
            logger.setAdditive(false);
            logger.addAppender(appender);

            // Emit logs
            logger.info("User alice logged in");
            logger.warn("Slow query detected on table orders");

            // Give a moment for HTTP delivery (synchronous, but just in case)
            Thread.sleep(100);

            assertThat(receivedBodies).hasSize(2);
            assertThat(receivedBodies.get(0)).contains("User alice logged in");
            assertThat(receivedBodies.get(1)).contains("Slow query detected on table orders");

            // Cleanup
            logger.detachAppender(appender);
            appender.stop();
            encoder.stop();
        }
    }
}
