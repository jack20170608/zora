package top.ilovemyhome.zora.poc.tui.tamboui;

import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WorkbenchScreenTest {

    @Test
    void showsThreePanelsAndDegradesOnSmallTerminals() throws Exception {
        try (var screen = new WorkbenchScreen();
             var test = ToolkitTestRunner.runTest(screen::render, new Size(120, 32))) {
            attach(screen, test);
            var pilot = test.pilot();
            await(test, () -> pilot.hasElement("tasks") && pilot.hasElement("logs")
                    && pilot.hasElement("details") && pilot.hasElement("command"));

            pilot.resize(90, 24);
            await(test, () -> !pilot.hasElement("details") && pilot.hasElement("logs"));

            pilot.resize(40, 10);
            await(test, () -> pilot.hasElement("terminal-too-small") && !pilot.hasElement("command"));

            pilot.resize(120, 32);
            await(test, () -> pilot.hasElement("details") && pilot.hasElement("command"));
        }
    }

    @Test
    void switchesFocusAndSubmitsChineseTextWithoutTreatingQAsQuit() throws Exception {
        var state = new WorkbenchState();
        try (var screen = new WorkbenchScreen(state, new MockTaskRunner());
             var test = ToolkitTestRunner.runTest(screen::render, new Size(120, 32))) {
            attach(screen, test);
            var pilot = test.pilot();
            pilot.press(KeyCode.TAB);
            await(test, () -> test.runner().focusManager().isFocused("logs"));
            pilot.press(KeyCode.TAB, KeyModifiers.SHIFT);
            await(test, () -> test.runner().focusManager().isFocused("tasks"));
            pilot.press(KeyCode.DOWN);
            await(test, () -> state.selected() == 1);

            pilot.click("command");
            await(test, () -> test.runner().focusManager().isFocused("command"));
            for (char c : "note 中文q".toCharArray()) {
                pilot.press(c);
            }
            pilot.press(KeyCode.ENTER);
            await(test, () -> state.logs().contains("Note: 中文q"));
            assertThat(test.runner().isRunning()).isTrue();

            pilot.press(KeyCode.F1);
            await(test, () -> pilot.hasElement("modal") && !pilot.hasElement("command"));
            pilot.press(KeyCode.F5);
            pilot.press(KeyCode.ESCAPE);
            await(test, () -> pilot.hasElement("command")
                    && test.runner().focusManager().isFocused("command"));
            assertThat(onUi(test, state::isRunning)).isFalse();
        }
    }

    @Test
    void runsCancelsAndRetriesTasksWithoutBlockingInput() throws Exception {
        var state = new WorkbenchState();
        try (var screen = new WorkbenchScreen(state, new MockTaskRunner(Duration.ofMillis(20)));
             var test = ToolkitTestRunner.runTest(screen::render, new Size(120, 32))) {
            attach(screen, test);
            var pilot = test.pilot();
            pilot.press(KeyCode.F5);
            await(test, state::isRunning);
            pilot.press(KeyCode.TAB);
            await(test, () -> test.runner().focusManager().isFocused("logs"));
            pilot.press(KeyCode.F6);
            await(test, () -> state.activeTask().status() == WorkbenchState.Status.CANCELLED);

            pilot.press(KeyCode.F5);
            await(test, () -> state.activeTask().status() == WorkbenchState.Status.COMPLETED);
            pilot.press(KeyCode.F9);
            await(test, () -> state.logs().isEmpty());
        }
    }

    @Test
    void confirmsExitAndRestoresFocusWhenExitIsDismissed() throws Exception {
        try (var screen = new WorkbenchScreen();
             var test = ToolkitTestRunner.runTest(screen::render, new Size(120, 32))) {
            attach(screen, test);
            var pilot = test.pilot();
            pilot.press(KeyCode.F10);
            await(test, () -> pilot.hasElement("modal"));
            pilot.press(KeyCode.TAB);
            pilot.press(KeyCode.ESCAPE);
            await(test, () -> pilot.hasElement("tasks")
                    && test.runner().focusManager().isFocused("tasks"));
            pilot.press(KeyCode.F10);
            await(test, () -> pilot.hasElement("modal"));
            pilot.press(KeyCode.ENTER);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (test.runner().isRunning() && System.nanoTime() < deadline) {
                pilot.pause(Duration.ofMillis(10));
            }
            assertThat(test.runner().isRunning()).isFalse();
        }
    }

    private static void attach(WorkbenchScreen screen, ToolkitTestRunner test) throws Exception {
        onUi(test, () -> {
            screen.attach(test.runner());
            return true;
        });
        await(test, () -> test.pilot().hasElement("tasks"));
    }

    private static <T> T onUi(ToolkitTestRunner test, Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        test.runner().runOnRenderThread(() -> {
            try {
                result.complete(action.call());
            } catch (Exception exception) {
                result.completeExceptionally(exception);
            }
        });
        return result.get(3, TimeUnit.SECONDS);
    }

    private static void await(ToolkitTestRunner test, Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (onUi(test, condition)) {
                return;
            }
            test.pilot().pause(Duration.ofMillis(10));
        }
        assertThat(onUi(test, condition)).as("UI condition becomes true within three seconds").isTrue();
    }
}
