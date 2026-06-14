package top.ilovemyhome.zora.poc.cui.claude;

/** Parses raw terminal input into shell commands or chat messages. */
final class ClaudeCuiCommandParser {

    ClaudeCuiCommand parse(String input) {
        String normalizedInput = input == null ? "" : input.trim();
        if (normalizedInput.isEmpty()) {
            return ClaudeCuiCommand.of(ClaudeCuiCommandType.EMPTY);
        }
        return switch (normalizedInput) {
            case ":help" -> ClaudeCuiCommand.of(ClaudeCuiCommandType.HELP);
            case ":clear" -> ClaudeCuiCommand.of(ClaudeCuiCommandType.CLEAR);
            case ":config", "/config" -> ClaudeCuiCommand.of(ClaudeCuiCommandType.CONFIG);
            case ":exit", ":quit" -> ClaudeCuiCommand.of(ClaudeCuiCommandType.EXIT);
            default -> parseDefault(normalizedInput);
        };
    }

    private ClaudeCuiCommand parseDefault(String normalizedInput) {
        if (normalizedInput.startsWith(":")) {
            return ClaudeCuiCommand.withContent(ClaudeCuiCommandType.UNKNOWN, normalizedInput);
        }
        return ClaudeCuiCommand.withContent(ClaudeCuiCommandType.CHAT, normalizedInput);
    }
}
