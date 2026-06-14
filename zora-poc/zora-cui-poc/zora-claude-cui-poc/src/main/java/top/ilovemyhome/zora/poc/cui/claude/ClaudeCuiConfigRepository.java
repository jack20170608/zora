package top.ilovemyhome.zora.poc.cui.claude;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persists Claude CUI configuration as a Java properties file. */
final class ClaudeCuiConfigRepository {

    private static final String MODEL_KEY = "model";
    private static final String THEME_KEY = "theme";
    private static final String STREAM_DELAY_MILLIS_KEY = "streamDelayMillis";

    private final Path configPath;

    ClaudeCuiConfigRepository(Path configPath) {
        if (configPath == null) {
            throw new IllegalArgumentException("configPath must not be null");
        }
        this.configPath = configPath;
    }

    ClaudeCuiConfig load() {
        if (!Files.isRegularFile(configPath) || !Files.isReadable(configPath)) {
            return ClaudeCuiConfig.defaultConfig();
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            return ClaudeCuiConfig.defaultConfig();
        }
        return ClaudeCuiConfig.fromValues(
            properties.getProperty(MODEL_KEY),
            properties.getProperty(THEME_KEY),
            parseStreamDelayMillis(properties.getProperty(STREAM_DELAY_MILLIS_KEY)));
    }

    void save(ClaudeCuiConfig config) {
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
                properties.store(outputStream, "Zora Claude CUI configuration");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save config", exception);
        }
    }

    private static long parseStreamDelayMillis(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return ClaudeCuiConfig.defaultConfig().streamDelayMillis();
        }
    }
}
