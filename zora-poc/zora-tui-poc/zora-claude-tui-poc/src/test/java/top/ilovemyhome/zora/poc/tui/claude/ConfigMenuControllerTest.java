package top.ilovemyhome.zora.poc.tui.claude;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigMenuControllerTest {

    @Test
    void configRepositorySaveAndLoadWorks(@TempDir Path tempDir) {
        // Test the repository directly since the controller now uses terminal raw mode
        Path configPath = tempDir.resolve("test.properties");
        ClaudeTuiConfigRepository repository = new ClaudeTuiConfigRepository(configPath);
        ClaudeTuiConfig original = ClaudeTuiConfig.defaultConfig();

        repository.save(original);
        ClaudeTuiConfig loaded = repository.load();

        assertThat(loaded).isEqualTo(original);
    }

    @Test
    void configRepositoryHandlesMissingFile(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("nonexistent.properties");
        ClaudeTuiConfigRepository repository = new ClaudeTuiConfigRepository(configPath);

        ClaudeTuiConfig loaded = repository.load();

        assertThat(loaded).isEqualTo(ClaudeTuiConfig.defaultConfig());
    }

    @Test
    void configRepositoryRoundTripWithDifferentValues(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("test.properties");
        ClaudeTuiConfigRepository repository = new ClaudeTuiConfigRepository(configPath);

        ClaudeTuiConfig toSave = ClaudeTuiConfig.fromValues("mock-opus", "light", 20L);
        repository.save(toSave);

        ClaudeTuiConfig loaded = repository.load();

        assertThat(loaded.model()).isEqualTo("mock-opus");
        assertThat(loaded.theme()).isEqualTo("light");
        assertThat(loaded.streamDelayMillis()).isEqualTo(20L);
    }

    @Test
    void configRepositoryUsesDefaultsForInvalidValues(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("test.properties");
        // Write invalid properties
        Files.writeString(configPath, """
            model=invalid-model
            theme=invalid-theme
            streamDelayMillis=not-a-number
            """);

        ClaudeTuiConfigRepository repository = new ClaudeTuiConfigRepository(configPath);
        ClaudeTuiConfig loaded = repository.load();

        assertThat(loaded.model()).isEqualTo("mock-claude");
        assertThat(loaded.theme()).isEqualTo("dark");
        assertThat(loaded.streamDelayMillis()).isEqualTo(8L);
    }

    @Test
    void configModelImmutableUpdateReturnsNewInstance() {
        ClaudeTuiConfig original = ClaudeTuiConfig.defaultConfig();
        ClaudeTuiConfig withModel = original.withModel("mock-opus");
        ClaudeTuiConfig withTheme = withModel.withTheme("light");
        ClaudeTuiConfig withDelay = withTheme.withStreamDelayMillis(20L);

        // Original should be unchanged
        assertThat(original.model()).isEqualTo("mock-claude");

        // New instances should have updated values
        assertThat(withModel.model()).isEqualTo("mock-opus");
        assertThat(withTheme.theme()).isEqualTo("light");
        assertThat(withDelay.streamDelayMillis()).isEqualTo(20L);
    }

    @Test
    void configModelAllowedValuesAreCorrect() {
        assertThat(ClaudeTuiConfig.allowedModels())
            .containsExactly("mock-claude", "mock-opus", "mock-sonnet");
        assertThat(ClaudeTuiConfig.allowedThemes())
            .containsExactly("light", "dark");
        assertThat(ClaudeTuiConfig.allowedStreamDelayMillis())
            .containsExactly(0L, 8L, 20L);
    }

    @Test
    void configModelDefaultValues() {
        ClaudeTuiConfig config = ClaudeTuiConfig.defaultConfig();

        assertThat(config.model()).isEqualTo("mock-claude");
        assertThat(config.theme()).isEqualTo("dark");
        assertThat(config.streamDelayMillis()).isEqualTo(8L);
    }
}