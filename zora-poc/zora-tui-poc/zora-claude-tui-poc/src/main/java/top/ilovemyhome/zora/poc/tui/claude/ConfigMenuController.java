package top.ilovemyhome.zora.poc.tui.claude;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;

/** Controls the interactive configuration menu with arrow key navigation for the Claude-like TUI shell. */
final class ConfigMenuController {

    private static final String ARROW_SELECTED = "► ";
    private static final String ARROW_NORMAL = "  ";
    private static final List<ConfigMenuOption> MODEL_OPTIONS = optionsFromValues(ClaudeTuiConfig.allowedModels());
    private static final List<ConfigMenuOption> THEME_OPTIONS = optionsFromValues(ClaudeTuiConfig.allowedThemes());
    private static final List<ConfigMenuOption> DELAY_OPTIONS = optionsFromValues(
        ClaudeTuiConfig.allowedStreamDelayMillis().stream()
            .map(String::valueOf)
            .toList());

    private final LineReader lineReader;
    private final Terminal terminal;
    private final PrintWriter writer;
    private final ClaudeTuiConfigRepository repository;

    ConfigMenuController(LineReader lineReader, Terminal terminal, PrintWriter writer, ClaudeTuiConfigRepository repository) {
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
        this.terminal = terminal;
        this.writer = writer;
        this.repository = repository;
    }

    ClaudeTuiConfig open(ClaudeTuiConfig currentConfig) {
        ClaudeTuiConfig original = currentConfig == null ? ClaudeTuiConfig.defaultConfig() : currentConfig;
        ClaudeTuiConfig draft = original;

        // Save terminal state and disable echo
        Attributes originalAttributes = terminal.getAttributes();
        terminal.setAttributes(terminal.enterRawMode());

        try {
            while (true) {
                int choice = showMainMenu(draft);
                switch (choice) {
                    case 0 -> { // Model
                        String selected = chooseOptionWithArrows("Select Model", MODEL_OPTIONS, draft.model());
                        if (selected != null) {
                            draft = draft.withModel(selected);
                        }
                    }
                    case 1 -> { // Theme
                        String selected = chooseOptionWithArrows("Select Theme", THEME_OPTIONS, draft.theme());
                        if (selected != null) {
                            draft = draft.withTheme(selected);
                        }
                    }
                    case 2 -> { // Stream delay
                        Long selected = chooseDelayWithArrows(draft);
                        if (selected != null) {
                            draft = draft.withStreamDelayMillis(selected);
                        }
                    }
                    case 3 -> { // Save
                        if (save(draft)) {
                            clearScreen();
                            printSaveSuccess();
                            return draft;
                        }
                    }
                    case 4 -> { // Back
                        clearScreen();
                        printUnchanged();
                        return original;
                    }
                    case -1 -> { // ESC pressed
                        clearScreen();
                        printUnchanged();
                        return original;
                    }
                }
            }
        } finally {
            terminal.setAttributes(originalAttributes);
        }
    }

    private int showMainMenu(ClaudeTuiConfig draft) {
        String[] menuItems = new String[] {
            "Model: " + draft.model(),
            "Theme: " + draft.theme(),
            "Stream Delay: " + draft.streamDelayMillis() + "ms",
            "",
            "Save & Exit",
            "Back"
        };
        int[] actions = {0, 1, 2, -1, 3, 4};
        int selectableStart = 0;
        int selectableEnd = 5;

        return runMenu("Configuration", menuItems, actions, selectableStart, selectableEnd);
    }

    private String chooseOptionWithArrows(String title, List<ConfigMenuOption> options, String currentValue) {
        String[] menuItems = options.stream()
            .map(opt -> opt.label() + (opt.value().equals(currentValue) ? " ✓" : ""))
            .toArray(String[]::new);
        int[] actions = new int[options.size()];
        for (int i = 0; i < options.size(); i++) {
            actions[i] = i;
        }

        int choice = runMenu(title, menuItems, actions, 0, options.size() - 1);

        if (choice >= 0 && choice < options.size()) {
            return options.get(choice).value();
        }
        return null;
    }

    private Long chooseDelayWithArrows(ClaudeTuiConfig draft) {
        long[] values = {0L, 8L, 20L};
        String[] labels = {"0ms (instant)", "8ms (default)", "20ms (slow)"};

        String[] menuItems = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            menuItems[i] = labels[i] + (draft.streamDelayMillis() == values[i] ? " ✓" : "");
        }

        int[] actions = {0, 1, 2};
        int choice = runMenu("Select Stream Delay", menuItems, actions, 0, 2);

        if (choice >= 0) {
            return values[choice];
        }
        return null;
    }

    private int runMenu(String title, String[] menuItems, int[] actions, int selectableStart, int selectableEnd) {
        int selectedIndex = selectableStart;
        boolean done = false;
        int result = -1;

        while (!done) {
            clearScreen();
            printMenu(title, menuItems, selectedIndex);

            int key = readKey();
            switch (key) {
                case -1, 27 -> { // ESC
                    result = -1;
                    done = true;
                }
                case 10, 13 -> { // Enter
                    if (selectedIndex >= selectableStart && selectedIndex <= selectableEnd) {
                        result = actions[selectedIndex];
                        done = true;
                    }
                }
                case -2 -> { // Up arrow
                    if (selectedIndex > selectableStart) {
                        selectedIndex--;
                    } else {
                        selectedIndex = selectableEnd;
                    }
                }
                case -3 -> { // Down arrow
                    if (selectedIndex < selectableEnd) {
                        selectedIndex++;
                    } else {
                        selectedIndex = selectableStart;
                    }
                }
            }
        }

        return result;
    }

    private void printMenu(String title, String[] menuItems, int selectedIndex) {
        writer.println("╭──────────────────────────────────────────╮");
        writer.printf("│ %-38s │%n", title);
        writer.println("├──────────────────────────────────────────┤");

        for (int i = 0; i < menuItems.length; i++) {
            String prefix = (i == selectedIndex) ? ARROW_SELECTED : ARROW_NORMAL;
            writer.printf("│ %s%-36s │%n", prefix, menuItems[i]);
        }

        writer.println("├──────────────────────────────────────────┤");
        writer.println("│ [↑/↓] Navigate  [Enter] Select  [ESC] │");
        writer.println("╰──────────────────────────────────────────╯");
        writer.flush();
    }

    private int readKey() {
        try {
            int c = terminal.reader().read();
            if (c == 27) { // ESC
                // Check for arrow key sequence
                int next = terminal.reader().read();
                if (next == 91) { // '['
                    int arrow = terminal.reader().read();
                    switch (arrow) {
                        case 65 -> { // Up arrow
                            return -2;
                        }
                        case 66 -> { // Down arrow
                            return -3;
                        }
                    }
                }
                return 27;
            }
            return c;
        } catch (java.io.IOException exception) {
            return -1;
        }
    }

    private void clearScreen() {
        writer.print("\033[H\033[2J");
        writer.flush();
    }

    private boolean save(ClaudeTuiConfig draft) {
        try {
            repository.save(draft);
            return true;
        } catch (IllegalStateException exception) {
            writer.println("Failed to save config: " + exception.getMessage());
            writer.flush();
            return false;
        }
    }

    private void printSaveSuccess() {
        writer.println("╭──────────────────────────────────────────╮");
        writer.println("│ ✓ Configuration saved successfully!     │");
        writer.println("╰──────────────────────────────────────────╯");
        writer.flush();
    }

    private void printUnchanged() {
        writer.println("Configuration unchanged.");
        writer.flush();
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
}