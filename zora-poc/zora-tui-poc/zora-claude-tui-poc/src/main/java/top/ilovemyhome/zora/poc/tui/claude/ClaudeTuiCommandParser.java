package top.ilovemyhome.zora.poc.tui.claude;

/** Parses raw terminal input into shell commands or chat messages. */
final class ClaudeTuiCommandParser {

    ClaudeTuiCommand parse(String input) {
        String normalizedInput = input == null ? "" : input.trim();
        if (normalizedInput.isEmpty()) {
            return ClaudeTuiCommand.of(ClaudeTuiCommandType.EMPTY);
        }
        return switch (normalizedInput) {
            case ":help" -> ClaudeTuiCommand.of(ClaudeTuiCommandType.HELP);
            case ":clear" -> ClaudeTuiCommand.of(ClaudeTuiCommandType.CLEAR);
            case ":config", "/config" -> ClaudeTuiCommand.of(ClaudeTuiCommandType.CONFIG);
            case ":exit", ":quit" -> ClaudeTuiCommand.of(ClaudeTuiCommandType.EXIT);
            default -> parseDefault(normalizedInput);
        };
    }

    private ClaudeTuiCommand parseDefault(String normalizedInput) {
        if (normalizedInput.startsWith(":")) {
            return ClaudeTuiCommand.withContent(ClaudeTuiCommandType.UNKNOWN, normalizedInput);
        }
        return ClaudeTuiCommand.withContent(ClaudeTuiCommandType.CHAT, normalizedInput);
    }
}
