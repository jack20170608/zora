package top.ilovemyhome.zora.poc.cui.claude;

import java.io.PrintWriter;
import java.nio.file.Path;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/** Application entry point for the JLine-based Claude-like CUI POC. */
public final class ClaudeCuiApplication {

    private static final Path CONFIG_PATH = Path.of(
        "zora-poc",
        "zora-cui-poc",
        "zora-claude-cui-poc",
        "config",
        "zora-claude-cui.properties");

    private ClaudeCuiApplication() {
    }

    public static void main(String[] args) {
        try {
            runShell();
        } catch (Exception exception) {
            System.err.println("Failed to start zora-claude-cui-poc: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void runShell() throws Exception {
        ClaudeCuiConfigRepository configRepository = new ClaudeCuiConfigRepository(CONFIG_PATH);
        ClaudeCuiConfig config = configRepository.load();
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("zora-claude-cui-poc")
                .build();
            PrintWriter writer = terminal.writer();
            ConfigMenuController configMenuController = new ConfigMenuController(
                lineReader,
                writer,
                configRepository);
            ClaudeCuiShell shell = new ClaudeCuiShell(
                lineReader,
                terminal,
                new ClaudeCuiCommandParser(),
                new MockAssistant(),
                new StreamingPrinter(writer, config.streamDelayMillis()),
                configMenuController,
                config);
            shell.run();
        }
    }
}
