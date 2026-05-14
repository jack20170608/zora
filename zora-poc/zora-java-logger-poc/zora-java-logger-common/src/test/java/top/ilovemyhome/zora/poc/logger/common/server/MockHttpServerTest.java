package top.ilovemyhome.zora.poc.logger.common.server;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MockHttpServer}.
 */
class MockHttpServerTest {

    @Test
    void testServerStartsOnRandomPortAndResponds() throws Exception {
        try (MockHttpServer server = MockHttpServer.create(0)) {
            server.registerTextHandler("/hello", 200, "Hello, Logger!");
            server.start();

            assertThat(server.port()).isGreaterThan(0);

            URI uri = server.baseUri().resolve("hello");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            int status = conn.getResponseCode();
            assertThat(status).isEqualTo(200);

            String body;
            try (InputStream is = conn.getInputStream()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertThat(body).isEqualTo("Hello, Logger!");
        }
    }

    @Test
    void testCustomHandler() throws Exception {
        try (MockHttpServer server = MockHttpServer.create(0)) {
            server.registerHandler("/api/log", exchange -> {
                String method = exchange.getRequestMethod();
                byte[] response = ("Method: " + method).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(201, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();

            URI uri = server.baseUri().resolve("api/log");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            int status = conn.getResponseCode();
            assertThat(status).isEqualTo(201);

            String body;
            try (InputStream is = conn.getInputStream()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertThat(body).isEqualTo("Method: POST");
        }
    }
}
