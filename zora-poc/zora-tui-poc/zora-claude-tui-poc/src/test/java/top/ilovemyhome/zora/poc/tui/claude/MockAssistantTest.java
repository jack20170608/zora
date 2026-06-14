package top.ilovemyhome.zora.poc.tui.claude;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockAssistantTest {

    private final MockAssistant assistant = new MockAssistant();

    @Test
    void responseIncludesTurnNumberAndUserMessage() {
        String response = assistant.respond("hello", ConversationState.initial().nextTurn());

        assertThat(response).contains("Turn 1");
        assertThat(response).contains("hello");
        assertThat(response).contains("mock streaming response");
    }

    @Test
    void responseIsDeterministicForSameInputAndState() {
        ConversationState state = new ConversationState(3, false);

        String first = assistant.respond("same", state);
        String second = assistant.respond("same", state);

        assertThat(first).isEqualTo(second);
    }
}
