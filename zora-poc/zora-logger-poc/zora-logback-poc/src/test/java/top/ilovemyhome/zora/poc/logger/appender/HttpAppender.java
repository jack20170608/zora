package top.ilovemyhome.zora.poc.logger.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Custom Logback appender that sends log events to a remote HTTP endpoint.
 *
 * <p>This is a POC implementation demonstrating how to build a custom appender.
 * In production, consider using an async wrapper ({@code AsyncAppender}) to avoid
 * blocking the caller thread during HTTP I/O.</p>
 *
 * <p>Usage (programmatic):</p>
 * <pre>{@code
 * HttpAppender appender = new HttpAppender();
 * appender.setEndpoint("http://localhost:8080/api/log");
 * appender.setEncoder(encoder);
 * appender.start();
 * logger.addAppender(appender);
 * }</pre>
 */
public class HttpAppender extends AppenderBase<ILoggingEvent> {

    private String endpoint;
    private Encoder<ILoggingEvent> encoder;

    /**
     * Sets the HTTP endpoint URL to which log events are POSTed.
     *
     * @param endpoint the target URL, e.g. {@code http://localhost:8080/api/log}
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Sets the encoder used to serialize log events before transmission.
     *
     * @param encoder the Logback encoder
     */
    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    @Override
    public void start() {
        if (endpoint == null || endpoint.isBlank()) {
            addError("No endpoint configured for HttpAppender");
            return;
        }
        if (encoder == null) {
            addError("No encoder configured for HttpAppender");
            return;
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) {
            return;
        }
        try {
            byte[] payload = encoder.encode(event);
            sendHttpPost(payload);
        } catch (Exception e) {
            addError("Failed to send log event to " + endpoint, e);
        }
    }

    private void sendHttpPost(byte[] payload) throws IOException {
        URL url = URI.create(endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        conn.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
        conn.setFixedLengthStreamingMode(payload.length);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            addWarn("HTTP endpoint returned status " + responseCode);
        }
        conn.disconnect();
    }
}
