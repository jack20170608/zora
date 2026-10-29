package top.ilovemyhome.zora.poc.tui.tamboui;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Runs bounded mock work without accessing terminal or UI state. */
final class MockTaskRunner implements AutoCloseable {

    record Update(long runId, int progress, String message, boolean failed) {
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("tamboui-mock-worker").factory());
    private final Duration stepDelay;
    private Future<?> current;

    MockTaskRunner() {
        this(Duration.ofMillis(200));
    }

    MockTaskRunner(Duration stepDelay) {
        this.stepDelay = Objects.requireNonNull(stepDelay);
        if (stepDelay.isNegative() || stepDelay.isZero()) {
            throw new IllegalArgumentException("Step delay must be positive");
        }
    }

    void start(long runId, String taskName, boolean simulateFailure, Consumer<Update> updates) {
        Objects.requireNonNull(updates);
        cancel();
        current = executor.submit(() -> {
            try {
                for (int step = 1; step <= 20; step++) {
                    Thread.sleep(stepDelay);
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    int progress = step * 5;
                    if (simulateFailure && progress == 50) {
                        updates.accept(new Update(runId, progress, "Mock failure: " + taskName, true));
                        return;
                    }
                    updates.accept(new Update(runId, progress,
                            taskName + " - " + progress + "%" + (progress == 100 ? " complete" : ""), false));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
    }

    void cancel() {
        if (current != null) {
            current.cancel(true);
            current = null;
        }
    }

    @Override
    public void close() {
        cancel();
        executor.shutdownNow();
    }
}
