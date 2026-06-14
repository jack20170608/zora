package top.ilovemyhome.zora.poc.cui.claude;

/** Deterministic assistant used to validate the CUI without calling a real LLM API. */
final class MockAssistant {

    String respond(String userMessage, ConversationState state) {
        String safeMessage = userMessage == null ? "" : userMessage;
        return "Turn " + state.turnCount() + ": I received: " + safeMessage + System.lineSeparator()
            + "This is a mock streaming response from zora-claude-cui-poc.";
    }
}
