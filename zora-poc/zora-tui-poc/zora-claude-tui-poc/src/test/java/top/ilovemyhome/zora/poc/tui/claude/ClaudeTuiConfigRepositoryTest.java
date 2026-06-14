package top.ilovemyhome.zora.poc.tui.claude;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaudeTuiConfigRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadMissingFileReturnsDefaultConfig() {
        ClaudeTuiConfigRepository repository = new ClaudeTuiConfigRepository(configPath());

        ClaudeTuiConfig config = repository.load();

        assertThat(config.model()).isEqualTo("mock-claude");
        assertThat(config.theme()).isEqualTo("dark");
        assertThat(config.streamDelayMillis()).isEqualTo(8L);
    }

    @Test
    void saveAndLoadRoundTripCreatesParentDirectoryAndFile() {
        Path configPath = configPath();
        ClaudeTuiConfig savedConfig = ClaudeTuiConfig.fromValues("mock-opus", "light", 20L);
        ClaudeTuiConfigRepository repository = new ClaudeTuiConfigRepository(configPath);

        repository.save(savedConfig);
        ClaudeTuiConfig loadedConfig = new ClaudeTuiConfigRepository(configPath).load();

        assertThat(Files.isDirectory(configPath.getParent())).isTrue();
        assertThat(Files.isRegularFile(configPath)).isTrue();
        assertThat(loadedConfig.model()).isEqualTo("mock-opus");
        assertThat(loadedConfig.theme()).isEqualTo("light");
        assertThat(loadedConfig.streamDelayMillis()).isEqualTo(20L);
    }

    @Test
    void loadMissingKeysUsesDefaults() throws IOException {
        Path configPath = configPath();
        saveProperties(configPath, propertiesWith("model", "mock-sonnet"));
        ClaudeTuiConfigRepository repository = new ClaudeTuiConfigRepository(configPath);

        ClaudeTuiConfig config = repository.load();

        assertThat(config.model()).isEqualTo("mock-sonnet");
        assertThat(config.theme()).isEqualTo("dark");
        assertThat(config.streamDelayMillis()).isEqualTo(8L);
    }

    @Test
    void loadInvalidValuesUsesDefaultsIncludingNonNumericStreamDelay() throws IOException {
        Path configPath = configPath();
        Properties properties = propertiesWith("model", "bad-model");
        properties.setProperty("theme", "bad-theme");
        properties.setProperty("streamDelayMillis", "not-a-number");
        saveProperties(configPath, properties);
        ClaudeTuiConfigRepository repository = new ClaudeTuiConfigRepository(configPath);

        ClaudeTuiConfig config = repository.load();

        assertThat(config.model()).isEqualTo("mock-claude");
        assertThat(config.theme()).isEqualTo("dark");
        assertThat(config.streamDelayMillis()).isEqualTo(8L);
    }

    private Path configPath() {
        return tempDir.resolve("config/zora-claude-tui.properties");
    }

    private static Properties propertiesWith(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        return properties;
    }

    private static void saveProperties(Path configPath, Properties properties) throws IOException {
        Files.createDirectories(configPath.getParent());
        try (OutputStream outputStream = Files.newOutputStream(configPath)) {
            properties.store(outputStream, "test config");
        }
    }
}
