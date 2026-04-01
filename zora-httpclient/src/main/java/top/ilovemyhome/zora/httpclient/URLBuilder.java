package top.ilovemyhome.zora.httpclient;


import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;

public final class URLBuilder {

    public static URLBuilder of() {
        return new URLBuilder();
    }

    public static URLBuilder of(String host) {
        return new URLBuilder("https", host, 443);
    }

    public static URLBuilder of(String schema, String host, int port) {
        return new URLBuilder(schema, host, port);
    }

    public URLBuilder withSchema(String schema) {
        this.schema = schema;
        return this;
    }

    public URLBuilder withHost(String host) {
        this.host = host;
        return this;
    }

    public URLBuilder withPort(int port) {
        this.port = port;
        return this;
    }

    public URLBuilder addPath(String p) {
        if (Objects.isNull(paths)) {
            paths = new StringBuilder();
        }
        String regulatedPath = regulatePath(p);
        paths.append("/").append(regulatedPath);
        return this;
    }

    public URLBuilder addParam(String name, String value) {
        if (StringUtils.isBlank(name) || StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("Parammeter name and value should not be blank.");
        }
        if (Objects.isNull(params)) {
            params = new StringBuilder();
        }
        if (!params.toString().isEmpty()) {
            params.append("&");
        }
        params.append(name).append("=").append(value);
        return this;
    }

    public String getURL() {
        try {
            URI uri = new URI(schema, null, host, port
                , Optional.ofNullable(paths).map(StringBuilder::toString).orElse(null)
                , Optional.ofNullable(params).map(StringBuilder::toString).orElse(null)
                , null);
            return uri.toURL().toString();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public String getRelativeURL() {
        try {
            URI uri = new URI(null, null
                , Optional.ofNullable(paths).map(StringBuilder::toString).orElse(null)
                , Optional.ofNullable(params).map(StringBuilder::toString).orElse(null)
                , null);
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private URLBuilder() {
    }

    private URLBuilder(String schema, String host, int port) {
        this();
        this.schema = schema;
        this.host = host;
        this.port = port;
    }

    private String regulatePath(String path) {
        path = path.trim();
        if (!path.startsWith("https://") && !path.startsWith("http://") && path.contains("//")) {
            throw new IllegalArgumentException("Path: " + path + " invalid, should not contain consecutive /");
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private StringBuilder paths, params;

    private String schema, host;
    private int port;

}
