package top.ilovemyhome.zora.poc.tui.claude;

import java.io.PrintWriter;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

/** Main interactive loop for the Claude-like terminal shell. */
final class ClaudeTuiShell {

    private static final String PROMPT = "you > ";

    private final LineReader lineReader;
    private final Terminal terminal;
    private final ClaudeTuiCommandParser commandParser;
    private final MockAssistant assistant;
    private final ConfigMenuController configMenuController;
    private ClaudeTuiConfig config;
    private StreamingPrinter streamingPrinter;

    ClaudeTuiShell(
        LineReader lineReader,
        Terminal terminal,
        ClaudeTuiCommandParser commandParser,
        MockAssistant assistant,
        StreamingPrinter streamingPrinter,
        ConfigMenuController configMenuController,
        ClaudeTuiConfig config) {
        this.lineReader = lineReader;
        this.terminal = terminal;
        this.commandParser = commandParser;
        this.assistant = assistant;
        this.streamingPrinter = streamingPrinter;
        this.configMenuController = configMenuController;
        this.config = config;
    }

    void run() {
        ConversationState state = ConversationState.initial();
        printWelcome();
        while (!state.shouldExit()) {
            try {
                String input = lineReader.readLine(PROMPT);
                state = handleInput(input, state);
            } catch (UserInterruptException | EndOfFileException exception) {
                state = state.exit();
            }
        }
        writer().println("bye");
        writer().flush();
    }

    private ConversationState handleInput(String input, ConversationState state) {
        ClaudeTuiCommand command = commandParser.parse(input);
        return switch (command.type()) {
            case EMPTY -> state;
            case HELP -> {
                printHelp();
                yield state;
            }
            case CLEAR -> {
                clearScreen();
                printWelcome();
                yield state;
            }
            case CONFIG -> {
                config = configMenuController.open(config);
                streamingPrinter = streamingPrinter.withDelayMillis(config.streamDelayMillis());
                yield state;
            }
            case EXIT -> state.exit();
            case UNKNOWN -> {
                writer().println("Unknown command: " + command.content() + ". Type :help for commands.");
                writer().flush();
                yield state;
            }
            case CHAT -> respondToChat(command.content(), state);
        };
    }

    private ConversationState respondToChat(String message, ConversationState state) {
        ConversationState nextState = state.nextTurn();
        streamingPrinter.printAssistantMessage(assistant.respond(message, nextState));
        return nextState;
    }

    private void printWelcome() {
        writer().println("╭────────────────────────────────────────╮");
        writer().println("│ Zora Claude TUI POC                    │");
        writer().println("│ Type :help for commands, :exit to quit │");
        writer().println("╰────────────────────────────────────────╯");
        writer().flush();
    }

    private void printHelp() {
        writer().println("Commands:");
        writer().println("  :help    Show this help message");
        writer().println("  :clear   Clear the terminal and show the welcome banner");
        writer().println("  :config  Open the configuration menu");
        writer().println("  /config  Open the configuration menu");
        writer().println("  :exit    Exit the shell");
        writer().println("  :quit    Exit the shell");
        writer().flush();
    }

    private void clearScreen() {
        writer().print("\033[H\033[2J");
        writer().flush();
    }

    private PrintWriter writer() {
        return terminal.writer();
    }
}
