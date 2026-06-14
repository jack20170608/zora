package top.ilovemyhome.zora.poc.tui.claude;

import java.util.List;

/** Immutable runtime configuration for the Claude-like TUI shell. */
record ClaudeTuiConfig(String model, String theme, long streamDelayMillis) {

    private static final String DEFAULT_MODEL = "mock-claude";
    private static final String DEFAULT_THEME = "dark";
    private static final long DEFAULT_STREAM_DELAY_MILLIS = 8L;
    private static final List<String> ALLOWED_MODELS = List.of("mock-claude", "mock-opus", "mock-sonnet");
    private static final List<String> ALLOWED_THEMES = List.of("light", "dark");
    private static final List<Long> ALLOWED_STREAM_DELAY_MILLIS = List.of(0L, 8L, 20L);

    ClaudeTuiConfig {
        model = normalizeModel(model);
        theme = normalizeTheme(theme);
        streamDelayMillis = normalizeStreamDelayMillis(streamDelayMillis);
    }

    static ClaudeTuiConfig defaultConfig() {
        return new ClaudeTuiConfig(DEFAULT_MODEL, DEFAULT_THEME, DEFAULT_STREAM_DELAY_MILLIS);
    }

    static ClaudeTuiConfig fromValues(String model, String theme, long streamDelayMillis) {
        return new ClaudeTuiConfig(model, theme, streamDelayMillis);
    }

    static List<String> allowedModels() {
        return List.copyOf(ALLOWED_MODELS);
    }

    static List<String> allowedThemes() {
        return List.copyOf(ALLOWED_THEMES);
    }

    static List<Long> allowedStreamDelayMillis() {
        return List.copyOf(ALLOWED_STREAM_DELAY_MILLIS);
    }

    ClaudeTuiConfig withModel(String model) {
        return new ClaudeTuiConfig(model, theme, streamDelayMillis);
    }

    ClaudeTuiConfig withTheme(String theme) {
        return new ClaudeTuiConfig(model, theme, streamDelayMillis);
    }

    ClaudeTuiConfig withStreamDelayMillis(long streamDelayMillis) {
        return new ClaudeTuiConfig(model, theme, streamDelayMillis);
    }

    private static String normalizeModel(String model) {
        return model != null && ALLOWED_MODELS.contains(model) ? model : DEFAULT_MODEL;
    }

    private static String normalizeTheme(String theme) {
        return theme != null && ALLOWED_THEMES.contains(theme) ? theme : DEFAULT_THEME;
    }

    private static long normalizeStreamDelayMillis(long streamDelayMillis) {
        return ALLOWED_STREAM_DELAY_MILLIS.contains(streamDelayMillis)
            ? streamDelayMillis
            : DEFAULT_STREAM_DELAY_MILLIS;
    }
}
