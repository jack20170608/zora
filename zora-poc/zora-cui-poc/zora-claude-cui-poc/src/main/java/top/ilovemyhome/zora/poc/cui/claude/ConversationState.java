package top.ilovemyhome.zora.poc.cui.claude;

/** Immutable shell conversation state. */
record ConversationState(int turnCount, boolean shouldExit) {

    ConversationState {
        if (turnCount < 0) {
            throw new IllegalArgumentException("turnCount must not be negative");
        }
    }

    static ConversationState initial() {
        return new ConversationState(0, false);
    }

    ConversationState nextTurn() {
        return new ConversationState(turnCount + 1, shouldExit);
    }

    ConversationState exit() {
        return new ConversationState(turnCount, true);
    }
}
