package top.ilovemyhome.zora.poc.cui.claude;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

/** Controls the interactive configuration menu for the Claude-like CUI shell. */
final class ConfigMenuController {

    private static final String PROMPT = "config > ";
    private static final List<ConfigMenuOption> MODEL_OPTIONS = optionsFromValues(ClaudeCuiConfig.allowedModels());
    private static final List<ConfigMenuOption> THEME_OPTIONS = optionsFromValues(ClaudeCuiConfig.allowedThemes());
    private static final List<ConfigMenuOption> DELAY_OPTIONS = optionsFromValues(
        ClaudeCuiConfig.allowedStreamDelayMillis().stream()
            .map(String::valueOf)
            .toList());

    private final LineReader lineReader;
    private final PrintWriter writer;
    private final ClaudeCuiConfigRepository repository;

    ConfigMenuController(LineReader lineReader, PrintWriter writer, ClaudeCuiConfigRepository repository) {
        if (lineReader == null) {
            throw new IllegalArgumentException("lineReader must not be null");
        }
        if (writer == null) {
            throw new IllegalArgumentException("writer must not be null");
        }
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.lineReader = lineReader;
        this.writer = writer;
        this.repository = repository;
    }

    ClaudeCuiConfig open(ClaudeCuiConfig currentConfig) {
        ClaudeCuiConfig original = currentConfig == null ? ClaudeCuiConfig.defaultConfig() : currentConfig;
        ClaudeCuiConfig draft = original;
        while (true) {
            printMainMenu(draft);
            String input = readInput();
            if (input == null || isBack(input)) {
                printUnchanged();
                return original;
            }
            try {
                switch (input) {
                    case "1" -> draft = chooseModel(draft);
                    case "2" -> draft = chooseTheme(draft);
                    case "3" -> draft = chooseStreamDelay(draft);
                    case "4" -> {
                        if (save(draft)) {
                            return draft;
                        }
                    }
                    case "5" -> {
                        printUnchanged();
                        return original;
                    }
                    default -> printUnknownOption();
                }
            } catch (ConfigMenuExitException exception) {
                printUnchanged();
                return original;
            }
        }
    }

    private ClaudeCuiConfig chooseModel(ClaudeCuiConfig draft) {
        ConfigMenuOption selectedOption = chooseOption("Select model:", MODEL_OPTIONS);
        return selectedOption == null ? draft : draft.withModel(selectedOption.value());
    }

    private ClaudeCuiConfig chooseTheme(ClaudeCuiConfig draft) {
        ConfigMenuOption selectedOption = chooseOption("Select theme:", THEME_OPTIONS);
        return selectedOption == null ? draft : draft.withTheme(selectedOption.value());
    }

    private ClaudeCuiConfig chooseStreamDelay(ClaudeCuiConfig draft) {
        ConfigMenuOption selectedOption = chooseOption("Select stream delay:", DELAY_OPTIONS);
        return selectedOption == null ? draft : draft.withStreamDelayMillis(Long.parseLong(selectedOption.value()));
    }

    private ConfigMenuOption chooseOption(String title, List<ConfigMenuOption> options) {
        while (true) {
            printSubMenu(title, options);
            String input = readInput();
            if (input == null) {
                throw new ConfigMenuExitException();
            }
            if (isBack(input)) {
                return null;
            }
            for (ConfigMenuOption option : options) {
                if (option.selector().equals(input)) {
                    return option;
                }
            }
            printUnknownOption();
        }
    }

    private boolean save(ClaudeCuiConfig draft) {
        try {
            repository.save(draft);
            writer.println("Config saved.");
            writer.flush();
            return true;
        } catch (IllegalStateException exception) {
            writer.println("Failed to save config: " + exception.getMessage());
            writer.flush();
            return false;
        }
    }

    private static List<ConfigMenuOption> optionsFromValues(List<String> values) {
        int selector = 1;
        List<ConfigMenuOption> options = new ArrayList<>();
        for (String value : values) {
            options.add(new ConfigMenuOption(String.valueOf(selector), value, value));
            selector++;
        }
        return List.copyOf(options);
    }

    private void printMainMenu(ClaudeCuiConfig draft) {
        writer.println("Config");
        writer.println("Current model: " + draft.model());
        writer.println("Current theme: " + draft.theme());
        writer.println("Current stream delay: " + draft.streamDelayMillis());
        writer.println("1. Model");
        writer.println("2. Theme");
        writer.println("3. Stream delay");
        writer.println("4. Save");
        writer.println("5. Back");
        writer.flush();
    }

    private void printSubMenu(String title, List<ConfigMenuOption> options) {
        writer.println(title);
        for (ConfigMenuOption option : options) {
            writer.println(option.selector() + ". " + option.label());
        }
        writer.println("b. Back");
        writer.flush();
    }

    private String readInput() {
        try {
            String input = lineReader.readLine(PROMPT);
            return input == null ? "" : input.trim().toLowerCase();
        } catch (EndOfFileException | UserInterruptException exception) {
            return null;
        }
    }

    private boolean isBack(String input) {
        return "b".equals(input) || "back".equals(input) || "q".equals(input) || "quit".equals(input);
    }

    private void printUnknownOption() {
        writer.println("Unknown config option. Please try again.");
        writer.flush();
    }

    private void printUnchanged() {
        writer.println("Config unchanged.");
        writer.flush();
    }

    private static final class ConfigMenuExitException extends RuntimeException {
    }
}
