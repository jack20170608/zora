package top.ilovemyhome.zora.poc.cui.claude;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConversationStateTest {

    @Test
    void startsWithZeroTurnsAndRunningStatus() {
        ConversationState state = ConversationState.initial();

        assertThat(state.turnCount()).isZero();
        assertThat(state.shouldExit()).isFalse();
    }

    @Test
    void incrementsTurnWithoutMutatingOriginalState() {
        ConversationState original = ConversationState.initial();
        ConversationState updated = original.nextTurn();

        assertThat(original.turnCount()).isZero();
        assertThat(updated.turnCount()).isEqualTo(1);
        assertThat(updated.shouldExit()).isFalse();
    }

    @Test
    void marksExitWithoutMutatingOriginalState() {
        ConversationState original = ConversationState.initial();
        ConversationState updated = original.exit();

        assertThat(original.shouldExit()).isFalse();
        assertThat(updated.shouldExit()).isTrue();
        assertThat(updated.turnCount()).isZero();
    }
}
