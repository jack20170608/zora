package top.ilovemyhome.zora.poc.cui.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigMenuControllerTest {

    @Mock
    private LineReader lineReader;

    @Mock
    private ClaudeCuiConfigRepository repository;

    private StringWriter output;
    private PrintWriter writer;
    private ConfigMenuController controller;

    @BeforeEach
    void setUp() {
        output = new StringWriter();
        writer = new PrintWriter(output, true);
        controller = new ConfigMenuController(lineReader, writer, repository);
    }

    @Test
    void selectingModelThenSaveWritesAndReturnsUpdatedConfig() {
        ClaudeCuiConfig original = ClaudeCuiConfig.defaultConfig();
        when(lineReader.readLine(anyString())).thenReturn("1", "2", "4");

        ClaudeCuiConfig result = controller.open(original);

        assertThat(result.model()).isEqualTo("mock-opus");
        assertThat(result.theme()).isEqualTo("dark");
        assertThat(result.streamDelayMillis()).isEqualTo(8L);
        verify(repository).save(result);
        assertThat(output.toString()).contains("Config", "Current model: mock-claude", "Config saved.");
    }

    @Test
    void selectingThemeAndStreamDelayThenSaveWritesAndReturnsUpdatedConfig() {
        ClaudeCuiConfig original = ClaudeCuiConfig.defaultConfig();
        when(lineReader.readLine(anyString())).thenReturn("2", "1", "3", "3", "4");

        ClaudeCuiConfig result = controller.open(original);

        assertThat(result.model()).isEqualTo("mock-claude");
        assertThat(result.theme()).isEqualTo("light");
        assertThat(result.streamDelayMillis()).isEqualTo(20L);
        verify(repository).save(result);
        assertThat(output.toString()).contains("Current theme: dark", "Current stream delay: 8", "Config saved.");
    }

    @Test
    void backWithoutSavingReturnsOriginalAndDoesNotPersistDraft() {
        ClaudeCuiConfig original = ClaudeCuiConfig.defaultConfig();
        when(lineReader.readLine(anyString())).thenReturn("1", "3", "5");

        ClaudeCuiConfig result = controller.open(original);

        assertThat(result).isSameAs(original);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(output.toString()).contains("Config unchanged.");
    }

    @Test
    void unknownInputPrintsMessageAndAllowsRetry() {
        ClaudeCuiConfig original = ClaudeCuiConfig.defaultConfig();
        when(lineReader.readLine(anyString())).thenReturn("unknown", "1", "3", "4");

        ClaudeCuiConfig result = controller.open(original);

        assertThat(result.model()).isEqualTo("mock-sonnet");
        verify(repository).save(result);
        assertThat(output.toString()).contains("Unknown config option. Please try again.", "Config saved.");
    }

    @Test
    void eofExitsUnchangedAndPrintsConfigUnchanged() {
        ClaudeCuiConfig original = ClaudeCuiConfig.fromValues("mock-opus", "light", 0L);
        when(lineReader.readLine(anyString())).thenThrow(new EndOfFileException());

        ClaudeCuiConfig result = controller.open(original);

        assertThat(result).isSameAs(original);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(output.toString()).contains("Config unchanged.");
    }

    @Test
    void eofInsideSubmenuExitsUnchangedAndDiscardsDraft() {
        ClaudeCuiConfig original = ClaudeCuiConfig.defaultConfig();
        when(lineReader.readLine(anyString()))
            .thenReturn("1", "2", "2")
            .thenThrow(new EndOfFileException())
            .thenReturn("4");

        ClaudeCuiConfig result = controller.open(original);

        assertThat(result).isSameAs(original);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(output.toString()).contains("Config unchanged.");
    }

    @Test
    void userInterruptInsideSubmenuExitsUnchangedAndLeavesRepositoryDefault(@TempDir Path tempDir) {
        ClaudeCuiConfig original = ClaudeCuiConfig.defaultConfig();
        Path configPath = tempDir.resolve("config.properties");
        ClaudeCuiConfigRepository realRepository = new ClaudeCuiConfigRepository(configPath);
        controller = new ConfigMenuController(lineReader, writer, realRepository);
        when(lineReader.readLine(anyString()))
            .thenReturn("1", "2", "2")
            .thenThrow(new UserInterruptException("interrupted"));

        ClaudeCuiConfig result = controller.open(original);

        assertThat(result).isSameAs(original);
        assertThat(realRepository.load()).isEqualTo(ClaudeCuiConfig.defaultConfig());
        assertThat(Files.exists(configPath)).isFalse();
        assertThat(output.toString()).contains("Config unchanged.");
    }

    @Test
    void saveFailurePrintsMessageAndStaysInMenu() {
        ClaudeCuiConfig original = ClaudeCuiConfig.defaultConfig();
        when(lineReader.readLine(anyString())).thenReturn("1", "2", "4", "5");
        doThrow(new IllegalStateException("disk full")).when(repository).save(org.mockito.ArgumentMatchers.any());

        ClaudeCuiConfig result = controller.open(original);

        assertThat(result).isSameAs(original);
        assertThat(output.toString()).contains("Failed to save config: disk full", "Config unchanged.");
    }
}
