# zora-claude-cui-poc Config Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persisted `:config` / `/config` question-and-answer menu to `zora-claude-cui-poc`.

**Architecture:** Keep the existing JLine REPL and command parser. Add an immutable config record, a properties-file repository, and a menu controller that runs as a temporary sub-loop from `ClaudeCuiShell`. Persist config in the leaf module's local `config/zora-claude-cui.properties` file and apply saved stream delay immediately to later assistant output.

**Tech Stack:** Maven, Java 25, JLine, Java `Properties`, JUnit 5, AssertJ, Mockito.

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandType.java` | Modify | Add `CONFIG` command type. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandParser.java` | Modify | Parse `:config` and `/config`. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandParserTest.java` | Modify | Test config aliases and slash chat fallback. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiConfig.java` | Create | Immutable runtime config and allowed values. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiConfigTest.java` | Create | Config defaults and copy-on-write tests. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiConfigRepository.java` | Create | Load/save Java properties config. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiConfigRepositoryTest.java` | Create | Repository persistence tests with temporary directories. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ConfigMenuController.java` | Create | Question-and-answer config menu. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ConfigMenuOption.java` | Create | Menu option value type. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ConfigMenuControllerTest.java` | Create | Menu behavior tests. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/StreamingPrinter.java` | Modify | Add `withDelayMillis(long)` copy method. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/StreamingPrinterTest.java` | Modify | Test delay copy validation. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiShell.java` | Modify | Open config menu and update runtime config/printer after save. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiApplication.java` | Modify | Load module-local config at startup and wire menu controller. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/README.md` | Modify | Document config menu and config file. |
| `docs/FEATURE-14-zora-claude-cui-config-menu.md` | Create | Chinese feature document required by project instructions. |

---

### Task 1: Add config command parsing

**Files:**
- Modify: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandParserTest.java`
- Modify: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandType.java`
- Modify: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandParser.java`

- [ ] **Step 1: Add failing parser tests**

In `ClaudeCuiCommandParserTest.java`, add these tests before `parsesUnknownColonCommand`:

```java
    @Test
    void parsesConfigAliases() {
        assertThat(parser.parse(":config").type()).isEqualTo(ClaudeCuiCommandType.CONFIG);
        assertThat(parser.parse("/config").type()).isEqualTo(ClaudeCuiCommandType.CONFIG);
    }

    @Test
    void treatsOtherSlashInputAsChatText() {
        ClaudeCuiCommand command = parser.parse("/hello");

        assertThat(command.type()).isEqualTo(ClaudeCuiCommandType.CHAT);
        assertThat(command.content()).isEqualTo("/hello");
    }
```

- [ ] **Step 2: Run parser tests to verify RED**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ClaudeCuiCommandParserTest test
```

Expected: compilation failure because `ClaudeCuiCommandType.CONFIG` does not exist.

- [ ] **Step 3: Add command type**

Replace `ClaudeCuiCommandType.java` with:

```java
package top.ilovemyhome.zora.poc.cui.claude;

/** Types of input supported by the Claude-like CUI shell. */
enum ClaudeCuiCommandType {
    HELP,
    CLEAR,
    CONFIG,
    EXIT,
    CHAT,
    EMPTY,
    UNKNOWN
}
```

- [ ] **Step 4: Parse config aliases**

Replace `ClaudeCuiCommandParser.java` with:

```java
package top.ilovemyhome.zora.poc.cui.claude;

/** Parses raw terminal input into shell commands or chat messages. */
final class ClaudeCuiCommandParser {

    ClaudeCuiCommand parse(String input) {
        String normalizedInput = input == null ? "" : input.trim();
        if (normalizedInput.isEmpty()) {
            return ClaudeCuiCommand.of(ClaudeCuiCommandType.EMPTY);
        }
        return switch (normalizedInput) {
            case ":help" -> ClaudeCuiCommand.of(ClaudeCuiCommandType.HELP);
            case ":clear" -> ClaudeCuiCommand.of(ClaudeCuiCommandType.CLEAR);
            case ":config", "/config" -> ClaudeCuiCommand.of(ClaudeCuiCommandType.CONFIG);
            case ":exit", ":quit" -> ClaudeCuiCommand.of(ClaudeCuiCommandType.EXIT);
            default -> parseDefault(normalizedInput);
        };
    }

    private ClaudeCuiCommand parseDefault(String normalizedInput) {
        if (normalizedInput.startsWith(":")) {
            return ClaudeCuiCommand.withContent(ClaudeCuiCommandType.UNKNOWN, normalizedInput);
        }
        return ClaudeCuiCommand.withContent(ClaudeCuiCommandType.CHAT, normalizedInput);
    }
}
```

- [ ] **Step 5: Run parser tests to verify GREEN**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ClaudeCuiCommandParserTest test
```

Expected: `Tests run: 8, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

---

### Task 2: Add immutable config model

**Files:**
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiConfigTest.java`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiConfig.java`

- [ ] **Step 1: Write failing config tests**

Create `ClaudeCuiConfigTest.java`:

```java
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
    void allowedValuesAreImmutableCopies() {
        assertThat(ClaudeCuiConfig.allowedModels()).containsExactly("mock-claude", "mock-opus", "mock-sonnet");
        assertThat(ClaudeCuiConfig.allowedThemes()).containsExactly("light", "dark");
        assertThat(ClaudeCuiConfig.allowedStreamDelayMillis()).containsExactly(0L, 8L, 20L);
    }
}
```

- [ ] **Step 2: Run config tests to verify RED**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ClaudeCuiConfigTest test
```

Expected: compilation failure because `ClaudeCuiConfig` does not exist.

- [ ] **Step 3: Implement config record**

Create `ClaudeCuiConfig.java`:

```java
package top.ilovemyhome.zora.poc.cui.claude;

import java.util.List;

/** Immutable runtime configuration for the Claude-like CUI shell. */
record ClaudeCuiConfig(String model, String theme, long streamDelayMillis) {

    private static final String DEFAULT_MODEL = "mock-claude";
    private static final String DEFAULT_THEME = "dark";
    private static final long DEFAULT_STREAM_DELAY_MILLIS = 8L;
    private static final List<String> ALLOWED_MODELS = List.of("mock-claude", "mock-opus", "mock-sonnet");
    private static final List<String> ALLOWED_THEMES = List.of("light", "dark");
    private static final List<Long> ALLOWED_STREAM_DELAY_MILLIS = List.of(0L, 8L, 20L);

    ClaudeCuiConfig {
        model = normalizeModel(model);
        theme = normalizeTheme(theme);
        streamDelayMillis = normalizeStreamDelayMillis(streamDelayMillis);
    }

    static ClaudeCuiConfig defaultConfig() {
        return new ClaudeCuiConfig(DEFAULT_MODEL, DEFAULT_THEME, DEFAULT_STREAM_DELAY_MILLIS);
    }

    static ClaudeCuiConfig fromValues(String model, String theme, long streamDelayMillis) {
        return new ClaudeCuiConfig(model, theme, streamDelayMillis);
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

    ClaudeCuiConfig withModel(String model) {
        return new ClaudeCuiConfig(model, theme, streamDelayMillis);
    }

    ClaudeCuiConfig withTheme(String theme) {
        return new ClaudeCuiConfig(model, theme, streamDelayMillis);
    }

    ClaudeCuiConfig withStreamDelayMillis(long streamDelayMillis) {
        return new ClaudeCuiConfig(model, theme, streamDelayMillis);
    }

    private static String normalizeModel(String model) {
        return ALLOWED_MODELS.contains(model) ? model : DEFAULT_MODEL;
    }

    private static String normalizeTheme(String theme) {
        return ALLOWED_THEMES.contains(theme) ? theme : DEFAULT_THEME;
    }

    private static long normalizeStreamDelayMillis(long streamDelayMillis) {
        return ALLOWED_STREAM_DELAY_MILLIS.contains(streamDelayMillis)
            ? streamDelayMillis
            : DEFAULT_STREAM_DELAY_MILLIS;
    }
}
```

- [ ] **Step 4: Run config tests to verify GREEN**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ClaudeCuiConfigTest test
```

Expected: `Tests run: 4, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

---

### Task 3: Add properties config repository

**Files:**
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiConfigRepositoryTest.java`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiConfigRepository.java`

- [ ] **Step 1: Write failing repository tests**

Create `ClaudeCuiConfigRepositoryTest.java` as described in the spec: use `@TempDir`, verify missing file loads defaults, save/load round trip, missing keys use defaults, invalid values use defaults.

- [ ] **Step 2: Run repository tests to verify RED**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ClaudeCuiConfigRepositoryTest test
```

Expected: compilation failure because `ClaudeCuiConfigRepository` does not exist.

- [ ] **Step 3: Implement repository**

Create `ClaudeCuiConfigRepository.java` with a constructor accepting `Path`, `load()` returning defaults on missing/unreadable files, and `save(ClaudeCuiConfig)` writing all three keys via Java `Properties` while creating parent directories.

- [ ] **Step 4: Run repository tests to verify GREEN**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ClaudeCuiConfigRepositoryTest test
```

Expected: `Tests run: 4, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

---

### Task 4: Add config menu controller

**Files:**
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ConfigMenuControllerTest.java`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ConfigMenuOption.java`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ConfigMenuController.java`

- [ ] **Step 1: Write failing menu tests**

Create `ConfigMenuControllerTest.java` with Mockito-backed `LineReader` and `StringWriter` output. Cover: selecting model then save, selecting theme and delay then save, back without saving, unknown input retry, EOF exits unchanged.

- [ ] **Step 2: Run menu tests to verify RED**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ConfigMenuControllerTest test
```

Expected: compilation failure because `ConfigMenuController` does not exist.

- [ ] **Step 3: Implement `ConfigMenuOption`**

Create a record with `selector`, `label`, and `value`, rejecting blank selector and normalizing null label/value to empty strings.

- [ ] **Step 4: Implement `ConfigMenuController`**

Create a final class that depends on `LineReader`, `PrintWriter`, and `ClaudeCuiConfigRepository`; loops on `config > `; renders the main menu and value menus; saves only on option `4`; returns original config on `5`, `b`, `back`, `q`, `quit`, EOF, or Ctrl+C.

- [ ] **Step 5: Run menu tests to verify GREEN**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ConfigMenuControllerTest test
```

Expected: `Tests run: 5, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

---

### Task 5: Wire config menu into shell and application

**Files:**
- Modify: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/StreamingPrinterTest.java`
- Modify: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/StreamingPrinter.java`
- Modify: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiShell.java`
- Modify: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiApplication.java`

- [ ] **Step 1: Add failing streaming printer copy test**

In `StreamingPrinterTest.java`, add a test that calls `new StreamingPrinter(writer, 0L).withDelayMillis(8L)` and asserts the returned printer is a different instance.

- [ ] **Step 2: Run streaming printer test to verify RED**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=StreamingPrinterTest test
```

Expected: compilation failure because `withDelayMillis(long)` does not exist.

- [ ] **Step 3: Add printer copy method**

Add `StreamingPrinter withDelayMillis(long delayMillis)` returning `new StreamingPrinter(writer, delayMillis)`.

- [ ] **Step 4: Run streaming printer test to verify GREEN**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=StreamingPrinterTest test
```

Expected: `Tests run: 3, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 5: Wire shell**

Update `ClaudeCuiShell` constructor to accept `ConfigMenuController` and `ClaudeCuiConfig`, store mutable shell references for current config and current `StreamingPrinter`, handle `CONFIG` by opening the menu, and update the printer delay after the returned config.

- [ ] **Step 6: Wire application**

Update `ClaudeCuiApplication` to create `ClaudeCuiConfigRepository` with `Path.of("zora-poc", "zora-cui-poc", "zora-claude-cui-poc", "config", "zora-claude-cui.properties")`, load config, create `ConfigMenuController`, and pass config delay to `StreamingPrinter`.

- [ ] **Step 7: Run full leaf tests**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc test
```

Expected: all tests pass and `BUILD SUCCESS`.

---

### Task 6: Update documentation and verify manually

**Files:**
- Modify: `zora-poc/zora-cui-poc/zora-claude-cui-poc/README.md`
- Create: `docs/FEATURE-14-zora-claude-cui-config-menu.md`

- [ ] **Step 1: Update README**

Document `:config`, `/config`, the module-local config file path, supported values, and the fact that no real Claude/Anthropic API is called.

- [ ] **Step 2: Create feature document**

Create `docs/FEATURE-14-zora-claude-cui-config-menu.md` in Chinese with background, scope, config file, interaction, security boundary, and verification commands.

- [ ] **Step 3: Run full tests**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Verify scripted config menu startup**

Run:

```bash
printf ':config\n1\n2\n4\n:exit\n' | mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc exec:java
```

Expected output contains `Config`, `Select model`, `Config saved.`, `bye`, and `BUILD SUCCESS`.

---

### Task 7: Final review and cleanup

**Files:**
- Review all changed files from this plan.

- [ ] **Step 1: Inspect git diff against main**

Run:

```bash
git diff main...HEAD --stat
```

Expected: changes only include config menu Java/tests/docs and the spec/plan files.

- [ ] **Step 2: Run final relevant tests**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run security scan by content**

Run:

```bash
grep -RInE "Anthropic|anthropic|apiKey|API_KEY|System\.getenv|HttpClient|URLConnection|Runtime\.getRuntime|ProcessBuilder" zora-poc/zora-cui-poc/zora-claude-cui-poc/src docs/FEATURE-14-zora-claude-cui-config-menu.md
```

Expected: no matches except README/doc text that explicitly says no real Anthropic API or API Key is used.

- [ ] **Step 4: Request Java code review**

Use the Java reviewer or code-review workflow on changed Java and Maven/doc files. Review must check config allow-lists, no secret/env loading, no shell/network execution, immutable config records, runtime delay update, and tests.

- [ ] **Step 5: Fix CRITICAL/HIGH review findings**

If review reports CRITICAL or HIGH issues, fix them and rerun:

```bash
mvn -pl zora-poc/zora-cui-poc test
```

Expected: `BUILD SUCCESS`.

---

## Self-Review

### Spec coverage

- `:config` and `/config` parsing is covered by Task 1.
- Immutable config model and allowed values are covered by Task 2.
- Properties persistence is covered by Task 3.
- Question-and-answer menu is covered by Task 4.
- Runtime shell/application wiring and immediate stream delay update are covered by Task 5.
- README and Chinese feature documentation are covered by Task 6.
- Final tests, scripted run, and security/code review are covered by Tasks 6 and 7.

### Placeholder scan

The plan intentionally avoids TBD/TODO placeholders. Some later steps describe implementation constraints instead of full replacement source to keep the plan concise, but all expected APIs, files, commands, and behavior are explicit.

### Type consistency

The plan consistently uses `ClaudeCuiConfig`, `ClaudeCuiConfigRepository`, `ConfigMenuController`, `ConfigMenuOption`, `ClaudeCuiCommandType.CONFIG`, `StreamingPrinter.withDelayMillis(long)`, `model`, `theme`, and `streamDelayMillis`.
