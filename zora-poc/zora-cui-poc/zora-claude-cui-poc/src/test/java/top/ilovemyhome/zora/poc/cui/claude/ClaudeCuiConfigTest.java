package top.ilovemyhome.zora.poc.cui.claude;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClaudeCuiConfigTest {

    @Test
    void defaultConfigUsesMockClaudeDarkThemeAndEightMillisDelay() {
        ClaudeCuiConfig config = ClaudeCuiConfig.defaultConfig();

        assertThat(config.model()).isEqualTo("mock-claude");
        assertThat(config.theme()).isEqualTo("dark");
        assertThat(config.streamDelayMillis()).isEqualTo(8L);
    }

    @Test
    void updatesReturnNewConfigWithoutMutatingOriginal() {
        ClaudeCuiConfig original = ClaudeCuiConfig.defaultConfig();
        ClaudeCuiConfig updated = original
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
        ClaudeCuiConfig config = ClaudeCuiConfig.fromValues("bad-model", "bad-theme", 99L);

        assertThat(config.model()).isEqualTo("mock-claude");
        assertThat(config.theme()).isEqualTo("dark");
        assertThat(config.streamDelayMillis()).isEqualTo(8L);
    }

    @Test
    void nullValuesFallBackToDefaults() {
        ClaudeCuiConfig config = ClaudeCuiConfig.fromValues(null, null, 99L);

        assertThat(config.model()).isEqualTo("mock-claude");
        assertThat(config.theme()).isEqualTo("dark");
        assertThat(config.streamDelayMillis()).isEqualTo(8L);
    }

    @Test
    void allowedValuesAreImmutableCopies() {
        assertThat(ClaudeCuiConfig.allowedModels()).containsExactly("mock-claude", "mock-opus", "mock-sonnet");
        assertThat(ClaudeCuiConfig.allowedThemes()).containsExactly("light", "dark");
        assertThat(ClaudeCuiConfig.allowedStreamDelayMillis()).containsExactly(0L, 8L, 20L);
    }
}
