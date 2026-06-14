package top.ilovemyhome.zora.poc.tui.claude;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persists Claude TUI configuration as a Java properties file. */
final class ClaudeTuiConfigRepository {

    private static final String MODEL_KEY = "model";
    private static final String THEME_KEY = "theme";
    private static final String STREAM_DELAY_MILLIS_KEY = "streamDelayMillis";

    private final Path configPath;

    ClaudeTuiConfigRepository(Path configPath) {
        if (configPath == null) {
            throw new IllegalArgumentException("configPath must not be null");
        }
        this.configPath = configPath;
    }

    ClaudeTuiConfig load() {
        if (!Files.isRegularFile(configPath) || !Files.isReadable(configPath)) {
            return ClaudeTuiConfig.defaultConfig();
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            return ClaudeTuiConfig.defaultConfig();
        }
        return ClaudeTuiConfig.fromValues(
            properties.getProperty(MODEL_KEY),
            properties.getProperty(THEME_KEY),
            parseStreamDelayMillis(properties.getProperty(STREAM_DELAY_MILLIS_KEY)));
    }

    void save(ClaudeTuiConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties properties = new Properties();
            properties.setProperty(MODEL_KEY, config.model());
            properties.setProperty(THEME_KEY, config.theme());
            properties.setProperty(STREAM_DELAY_MILLIS_KEY, Long.toString(config.streamDelayMillis()));
            try (OutputStream outputStream = Files.newOutputStream(configPath)) {
                properties.store(outputStream, "Zora Claude TUI configuration");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save config", exception);
        }
    }

    private static long parseStreamDelayMillis(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return ClaudeTuiConfig.defaultConfig().streamDelayMillis();
        }
    }
}
