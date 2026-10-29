package top.ilovemyhome.zora.poc.tui.tamboui;

import dev.tamboui.layout.Size;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.gauge;
import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textInput;

/** Declarative fullscreen UI. Only immutable worker updates cross the thread boundary. */
final class WorkbenchScreen implements AutoCloseable {

    private enum Modal { NONE, HELP, QUIT }

    private final WorkbenchState state;
    private final MockTaskRunner worker;
    private final ConcurrentLinkedQueue<MockTaskRunner.Update> updates = new ConcurrentLinkedQueue<>();
    private final TextInputState command = new TextInputState();
    private final ListElement<?> tasks = list().id("tasks").focusable()
            .autoScroll().highlightColor(Color.CYAN).scrollbar();
    private final ListElement<?> logs = list().id("logs").focusable()
            .displayOnly().stickyScroll().scrollbar();
    private ToolkitRunner runner;
    private Modal modal = Modal.NONE;
    private String previousFocus;

    WorkbenchScreen() {
        this(new WorkbenchState(), new MockTaskRunner());
    }

    WorkbenchScreen(WorkbenchState state, MockTaskRunner worker) {
        this.state = state;
        this.worker = worker;
        tasks.onKeyEvent(key -> {
            if (key.isKey(KeyCode.ENTER)) {
                startTask();
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        });
    }

    /** Attaches before entering the production loop; tests attach on their render thread. */
    void attach(ToolkitRunner runner) {
        this.runner = runner;
        runner.eventRouter().addGlobalHandler(this::handleGlobal);
    }

    Element render() {
        MockTaskRunner.Update update;
        while ((update = updates.poll()) != null) {
            state.accept(update);
        }
        state.select(tasks.selected());
        Size size = runner == null ? new Size(120, 32) : runner.tuiRunner().terminal().size();
        Element content;
        if (size.width() < 64 || size.height() < 18) {
            content = panel("Terminal too small",
                    text("Resize to at least 64 x 18."),
                    text("F10: exit  Ctrl+C: quit").dim())
                    .id("terminal-too-small").rounded().borderColor(Color.YELLOW).fill();
        } else {
            content = workbench(size.width() >= 110);
        }
        if (modal != Modal.NONE) {
            // Modal mode renders no background focus targets or event handlers.
            // This prevents Tab/mouse events from escaping into the workbench.
            return modalView();
        }
        return content;
    }

    private Element workbench(boolean showDetails) {
        tasks.items(state.tasks().stream()
                .map(task -> task.name() + " [" + task.status() + "]").toList());
        logs.items(state.logs());
        var selected = state.selectedTask();
        var taskPanel = panel("Tasks", tasks.fill()).rounded()
                .borderColor(focusColor("tasks")).length(26);
        var output = panel("Activity / " + selected.name(),
                text(selected.status() + " | " + selected.progress() + "%").length(1),
                gauge(selected.progress()).label(selected.progress() + "%").length(1),
                logs.fill()).rounded().borderColor(focusColor("logs")).fill();
        Element body = showDetails
                ? row(taskPanel, output, details().length(28)).fill()
                : row(taskPanel, output).fill();

        return column(
                panel(text("ZORA / TamboUI Task Workbench").bold().cyan())
                        .rounded().length(3),
                body,
                textInput(command).id("command")
                        .title("Command: run | cancel | clear | help | quit | note <text>")
                        .placeholder("Type a command or a note; Enter submits")
                        .rounded().borderColor(Color.BLUE).focusedBorderColor(Color.YELLOW)
                        .onSubmit(this::submitCommand).length(3),
                text("Tab/Shift+Tab: focus  F1: help  F5: run  F6: cancel  F9: clear  F10: exit")
                        .dim().length(1),
                text(state.message()).cyan().length(1)
        ).fill();
    }

    private dev.tamboui.toolkit.elements.Panel details() {
        var selected = state.selectedTask();
        return panel("Details",
                text(selected.name()).bold().length(1),
                text("State: " + selected.status()).length(1),
                text("Progress: " + selected.progress() + "%").length(1),
                text("Execution: local mock").length(1),
                text("Network: disabled").length(1),
                text("Logs: " + state.logs().size() + "/" + WorkbenchState.LOG_LIMIT).length(1),
                text(selected.simulateFailure() ? "Fails at 50% (demo)" : "Duration: about 4 seconds").length(1),
                text("F5 retry / F6 cancel").dim().length(1)
        ).id("details").rounded().borderColor(Color.BLUE);
    }

    private Color focusColor(String id) {
        return runner != null && runner.focusManager().isFocused(id) ? Color.YELLOW : Color.BLUE;
    }

    private EventResult handleGlobal(Event event) {
        if (!(event instanceof KeyEvent key)) {
            return EventResult.UNHANDLED;
        }
        if (key.isCtrlC()) {
            runner.quit();
            return EventResult.HANDLED;
        }
        if (modal != Modal.NONE) {
            return EventResult.HANDLED;
        }
        if (key.isKey(KeyCode.F1)) {
            openModal(Modal.HELP);
        } else if (key.isKey(KeyCode.F5)) {
            startTask();
        } else if (key.isKey(KeyCode.F6)) {
            cancelTask();
        } else if (key.isKey(KeyCode.F9)) {
            state.clearLogs();
        } else if (key.isKey(KeyCode.F10) || key.isQuit()) {
            openModal(Modal.QUIT);
        } else {
            return EventResult.UNHANDLED;
        }
        return EventResult.HANDLED;
    }

    private void startTask() {
        state.select(tasks.selected());
        long run = state.startSelected();
        if (run != 0) {
            var task = state.activeTask();
            worker.start(run, task.name(), task.simulateFailure(), updates::add);
        }
    }

    private void cancelTask() {
        worker.cancel();
        state.cancel();
    }

    private void submitCommand() {
        String input = command.text().strip();
        command.setText("");
        if (input.isEmpty()) {
            return;
        }
        switch (input.toLowerCase(Locale.ROOT)) {
            case "run" -> startTask();
            case "cancel" -> cancelTask();
            case "clear" -> state.clearLogs();
            case "help" -> openModal(Modal.HELP);
            case "quit", "exit" -> openModal(Modal.QUIT);
            default -> {
                String note = input.startsWith("note ") ? input.substring(5) : input;
                state.log("Note: " + note.substring(0, Math.min(note.length(), 512)));
            }
        }
    }

    private void openModal(Modal next) {
        previousFocus = runner.focusManager().focusedId();
        modal = next;
        runner.focusManager().setFocus("modal");
    }

    private void closeModal() {
        modal = Modal.NONE;
        runner.focusManager().setFocus(previousFocus == null ? "tasks" : previousFocus);
    }

    private Element modalView() {
        boolean quitting = modal == Modal.QUIT;
        var popup = quitting
                ? dialog("Exit workbench?",
                        text(state.isRunning() ? "The running mock task will be cancelled." : "No task is running."),
                        text("Enter: exit  Esc: return").yellow())
                : dialog("Workbench help",
                        text("Tab / Shift+Tab     Switch task / log / input focus"),
                        text("Up / Down           Navigate tasks or scroll logs"),
                        text("PgUp / PgDn / End   Browse logs / resume following"),
                        text("Enter on task       Start selected task"),
                        text("F5 / F6             Run / cancel mock task"),
                        text("F9 / F10            Clear logs / exit confirmation"),
                        text("Commands: run, cancel, clear, help, quit, note <text>"),
                        text("Ctrl+C quits immediately. Enter / Esc closes help."));
        return stack(popup.width(62).rounded().borderColor(Color.CYAN)
                .id("modal").focusable()
                .onKeyEvent(key -> {
                    if (key.isCtrlC()) {
                        runner.quit();
                        return EventResult.HANDLED;
                    }
                    return EventResult.UNHANDLED;
                })
                .onCancel(this::closeModal)
                .onConfirm(() -> {
                    if (quitting) {
                        runner.quit();
                    } else {
                        closeModal();
                    }
                })).fill();
    }

    @Override
    public void close() {
        worker.close();
        updates.clear();
    }
}
