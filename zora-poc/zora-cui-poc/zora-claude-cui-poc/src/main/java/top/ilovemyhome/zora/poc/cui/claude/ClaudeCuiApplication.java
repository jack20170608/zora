package top.ilovemyhome.zora.poc.cui.claude;

import java.io.PrintWriter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/** Application entry point for the JLine-based Claude-like CUI POC. */
public final class ClaudeCuiApplication {

    private static final long STREAM_DELAY_MILLIS = 8L;

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
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("zora-claude-cui-poc")
                .build();
            PrintWriter writer = terminal.writer();
            ClaudeCuiShell shell = new ClaudeCuiShell(
                lineReader,
                terminal,
                new ClaudeCuiCommandParser(),
                new MockAssistant(),
                new StreamingPrinter(writer, STREAM_DELAY_MILLIS));
            shell.run();
        }
    }
}
