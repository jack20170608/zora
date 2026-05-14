package top.ilovemyhome.zora.poc.logger.common.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight embedded HTTP server for logging integration tests.
 *
 * <p>Built on top of JDK {@link HttpServer}, requiring no external dependencies.
 * Provides a simple programmatic API to register handlers and start/stop the server.</p>
 *
 * <p>Intended for use in try-with-resources blocks:</p>
 * <pre>{@code
 * try (MockHttpServer server = MockHttpServer.create(0)) {
 *     server.registerHandler("/api/log", exchange -> {
 *         exchange.sendResponseHeaders(200, 0);
 *     });
 *     server.start();
 *     URI endpoint = server.baseUri().resolve("/api/log");
 *     // ... send requests and assert responses
 * }
 * }</pre>
 */
public class MockHttpServer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockHttpServer.class);

    private final HttpServer httpServer;
    private final ExecutorService executor;
    private boolean started = false;

    private MockHttpServer(HttpServer httpServer, ExecutorService executor) {
        this.httpServer = httpServer;
        this.executor = executor;
    }

    /**
     * Creates a new mock HTTP server bound to the given port.
     *
     * @param port the port to bind to; use 0 to let the system pick a free port
     * @return a new un-started server instance
     * @throws IOException if the server cannot be created
     */
    public static MockHttpServer create(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        ExecutorService exec = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mock-http-server-worker");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(exec);
        return new MockHttpServer(server, exec);
    }

    /**
     * Registers a handler for the given path.
     *
     * @param path     the request path (e.g. {@code "/api/log"})
     * @param handler  the handler to invoke for matching requests
     */
    public void registerHandler(String path, HttpHandler handler) {
        httpServer.createContext(path, handler);
    }

    /**
     * Registers a handler that returns a fixed plain-text response.
     *
     * @param path     the request path
     * @param status   the HTTP status code
     * @param body     the response body
     */
    public void registerTextHandler(String path, int status, String body) {
        httpServer.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
    }

    /**
     * Starts the server.
     */
    public void start() {
        if (started) {
            return;
        }
        httpServer.start();
        started = true;
        LOGGER.info("MockHttpServer started on {}", httpServer.getAddress());
    }

    /**
     * Stops the server immediately.
     */
    public void stop() {
        if (!started) {
            return;
        }
        httpServer.stop(0);
        executor.shutdownNow();
        started = false;
        LOGGER.info("MockHttpServer stopped");
    }

    /**
     * Returns the base URI of this server.
     *
     * @return {@code http://host:port/}
     */
    public URI baseUri() {
        InetSocketAddress address = httpServer.getAddress();
        return URI.create("http://" + address.getHostName() + ":" + address.getPort() + "/");
    }

    /**
     * Returns the actual port the server is listening on.
     *
     * @return the bound port number
     */
    public int port() {
        return httpServer.getAddress().getPort();
    }

    @Override
    public void close() {
        stop();
    }
}
