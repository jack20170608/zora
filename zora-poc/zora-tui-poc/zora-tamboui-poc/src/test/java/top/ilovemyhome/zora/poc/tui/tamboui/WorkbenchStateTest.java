package top.ilovemyhome.zora.poc.tui.tamboui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkbenchStateTest {

    @Test
    void startsOnlyOneTaskAndCompletesItThroughProgressEvents() {
        var state = new WorkbenchState();
        long run = state.startSelected();

        assertThat(run).isPositive();
        assertThat(state.startSelected()).isZero();
        assertThat(state.activeTask().status()).isEqualTo(WorkbenchState.Status.RUNNING);

        state.accept(new MockTaskRunner.Update(run, 40, "Mock step", false));
        assertThat(state.activeTask().progress()).isEqualTo(40);
        state.accept(new MockTaskRunner.Update(run, 100, "Done", false));

        assertThat(state.isRunning()).isFalse();
        assertThat(state.selectedTask().status()).isEqualTo(WorkbenchState.Status.COMPLETED);
        assertThat(state.logs()).contains("Done");
    }
}
