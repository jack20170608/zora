package top.ilovemyhome.zora.poc.tui.tamboui;

import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.tui.TuiConfig;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Starts the fullscreen POC in a real terminal, never a GUI or a dumb-terminal fallback. */
public final class TamboUiApplication {

    private TamboUiApplication() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            System.out.println("Zora TamboUI fullscreen workbench (Java 25+)");
            System.out.println("Run in Windows Terminal, an IDE terminal, or another interactive terminal.");
            System.out.println("F1 help | F5 run | F6 cancel | F9 clear | F10 exit | Ctrl+C quit");
            System.out.println("All tasks are mocks. No network, subprocesses, or database access.");
            return;
        }
        if (args.length != 0) {
            throw new IllegalArgumentException("Unknown arguments. Use --help for usage.");
        }

        try (Terminal terminal = TerminalBuilder.builder()
                .system(true).dumb(false).encoding(StandardCharsets.UTF_8).build()) {
            if (terminal.getType().startsWith("dumb") || terminal.getWidth() <= 0 || terminal.getHeight() <= 0) {
                throw new IOException("A real interactive terminal is required; run the JAR in Windows Terminal.");
            }
            var config = TuiConfig.builder()
                    .backend(new JLineBackend(terminal))
                    .mouseCapture(true)
                    .bracketedPaste(true)
                    .tickRate(Duration.ofMillis(50))
                    .pollTimeout(Duration.ofMillis(20))
                    .build();
            try (var screen = new WorkbenchScreen();
                 var runner = ToolkitRunner.create(config)) {
                screen.attach(runner);
                runner.run(screen::render);
            }
        } catch (IOException exception) {
            throw new IOException("Cannot open an interactive terminal. Use Windows Terminal or an IDE terminal, "
                    + "not a redirected pipe or the IDE Run console.", exception);
        }
    }
}
