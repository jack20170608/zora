package top.ilovemyhome.zora.poc.tui.tamboui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Owns task state on the render thread; late worker events cannot revive cancelled runs. */
final class WorkbenchState {

    static final int LOG_LIMIT = 200;

    enum Status { READY, RUNNING, COMPLETED, CANCELLED, FAILED }

    record Task(String name, String description, boolean simulateFailure, Status status, int progress) {
        Task withProgress(Status nextStatus, int nextProgress) {
            return new Task(name, description, simulateFailure, nextStatus, nextProgress);
        }
    }

    private final List<Task> tasks = new ArrayList<>(List.of(
            new Task("Build module", "Simulate a module build; no Maven command is executed.",
                    false, Status.READY, 0),
            new Task("Scan resources", "Simulate scanning local resources; no files are accessed.",
                    false, Status.READY, 0),
            new Task("Failure demo", "Fail at 50% to demonstrate error handling and retry.",
                    true, Status.READY, 0)));
    private final ArrayDeque<String> logs = new ArrayDeque<>();
    private int selected;
    private int active;
    private long runSequence;
    private String message = "Ready - select a task and press F5";

    WorkbenchState() {
        log("Welcome to the Zora TamboUI workbench. 中文输入测试 / Mock tasks only.");
    }

    List<Task> tasks() {
        return List.copyOf(tasks);
    }

    int selected() {
        return selected;
    }

    void select(int index) {
        selected = Math.clamp(index, 0, tasks.size() - 1);
    }

    Task selectedTask() {
        return tasks.get(selected);
    }

    Task activeTask() {
        return tasks.get(active);
    }

    boolean isRunning() {
        return activeTask().status() == Status.RUNNING;
    }

    long startSelected() {
        if (isRunning()) {
            message = "A task is already running; F6 cancels it";
            return 0;
        }
        active = selected;
        tasks.set(active, activeTask().withProgress(Status.RUNNING, 0));
        message = "Running: " + activeTask().name();
        log(message);
        return ++runSequence;
    }

    void accept(MockTaskRunner.Update update) {
        // A cancelled or superseded worker can still have an event in the queue.
        if (update.runId() != runSequence || !isRunning()) {
            return;
        }
        int progress = Math.clamp(update.progress(), activeTask().progress(), 100);
        Status status = update.failed() ? Status.FAILED
                : progress == 100 ? Status.COMPLETED : Status.RUNNING;
        tasks.set(active, activeTask().withProgress(status, progress));
        message = update.message();
        log(message);
    }

    void cancel() {
        if (isRunning()) {
            tasks.set(active, activeTask().withProgress(Status.CANCELLED, activeTask().progress()));
            message = "Cancelled: " + activeTask().name();
            log(message);
        } else {
            message = "No running task to cancel";
        }
    }

    void log(String line) {
        if (logs.size() == LOG_LIMIT) {
            logs.removeFirst();
        }
        logs.addLast(line);
    }

    List<String> logs() {
        return List.copyOf(logs);
    }

    void clearLogs() {
        logs.clear();
        message = "Logs cleared";
    }

    String message() {
        return message;
    }
}
