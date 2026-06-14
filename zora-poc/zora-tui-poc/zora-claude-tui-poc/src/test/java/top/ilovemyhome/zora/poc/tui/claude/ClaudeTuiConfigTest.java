package top.ilovemyhome.zora.poc.tui.claude;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClaudeTuiConfigTest {

    @Test
    void defaultConfigUsesMockClaudeDarkThemeAndEightMillisDelay() {
        ClaudeTuiConfig config = ClaudeTuiConfig.defaultConfig();

        assertThat(config.model()).isEqualTo("mock-claude");
        assertThat(config.theme()).isEqualTo("dark");
        assertThat(config.streamDelayMillis()).isEqualTo(8L);
    }

    @Test
    void updatesReturnNewConfigWithoutMutatingOriginal() {
        ClaudeTuiConfig original = ClaudeTuiConfig.defaultConfig();
        ClaudeTuiConfig updated = original
            .withModel("mock-opus")
            .withTheme("light")
            .withStreamDelayMillis(20L);

        assertThat(original.model()).isEqualTo("mock-claude");
        assertThat(original.theme()).isEqualTo("dark");
        assertThat(original.streamDelayMillis()).isEqualTo(8L);
        assertThat(updated.model()).isEqualTo("mock-opus");
        assertThat(updated.theme()).isEqualTo("light");
        assertThat(updated.streamDelayMillis()).isEqualTo(20L);
    }

    @Test
    void invalidValuesFallBackToDefaults() {
        ClaudeTuiConfig config = ClaudeTuiConfig.fromValues("bad-model", "bad-theme", 99L);

        assertThat(config.model()).isEqualTo("mock-claude");
        assertThat(config.theme()).isEqualTo("dark");
        assertThat(config.streamDelayMillis()).isEqualTo(8L);
    }

    @Test
    void nullValuesFallBackToDefaults() {
        ClaudeTuiConfig config = ClaudeTuiConfig.fromValues(null, null, 99L);

        assertThat(config.model()).isEqualTo("mock-claude");
        assertThat(config.theme()).isEqualTo("dark");
        assertThat(config.streamDelayMillis()).isEqualTo(8L);
    }

    @Test
    void allowedValuesAreImmutableCopies() {
        assertThat(ClaudeTuiConfig.allowedModels()).containsExactly("mock-claude", "mock-opus", "mock-sonnet");
        assertThat(ClaudeTuiConfig.allowedThemes()).containsExactly("light", "dark");
        assertThat(ClaudeTuiConfig.allowedStreamDelayMillis()).containsExactly(0L, 8L, 20L);
    }
}
