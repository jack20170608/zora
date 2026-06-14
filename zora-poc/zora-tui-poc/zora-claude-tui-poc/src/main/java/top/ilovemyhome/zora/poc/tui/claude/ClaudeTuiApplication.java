package top.ilovemyhome.zora.poc.tui.claude;

import java.io.PrintWriter;
import java.nio.file.Path;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/** Application entry point for the JLine-based Claude-like TUI POC. */
public final class ClaudeTuiApplication {

    private static final Path CONFIG_PATH = Path.of(
        "zora-poc",
        "zora-tui-poc",
        "zora-claude-tui-poc",
        "config",
        "zora-claude-tui.properties");

    private ClaudeTuiApplication() {
    }

    public static void main(String[] args) {
        try {
            runShell();
        } catch (Exception exception) {
            System.err.println("Failed to start zora-claude-tui-poc: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void runShell() throws Exception {
        ClaudeTuiConfigRepository configRepository = new ClaudeTuiConfigRepository(CONFIG_PATH);
        ClaudeTuiConfig config = configRepository.load();
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("zora-claude-tui-poc")
                .build();
            PrintWriter writer = terminal.writer();
            ConfigMenuController configMenuController = new ConfigMenuController(
                lineReader,
                terminal,
                writer,
                configRepository);
            ClaudeTuiShell shell = new ClaudeTuiShell(
                lineReader,
                terminal,
                new ClaudeTuiCommandParser(),
                new MockAssistant(),
                new StreamingPrinter(writer, config.streamDelayMillis()),
                configMenuController,
                config);
            shell.run();
        }
    }
}
