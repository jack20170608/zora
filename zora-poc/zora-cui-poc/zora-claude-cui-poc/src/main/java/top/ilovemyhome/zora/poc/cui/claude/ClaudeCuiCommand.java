package top.ilovemyhome.zora.poc.cui.claude;

/** Parsed user input with a command type and optional content. */
record ClaudeCuiCommand(ClaudeCuiCommandType type, String content) {

    ClaudeCuiCommand {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        content = content == null ? "" : content;
    }

    static ClaudeCuiCommand of(ClaudeCuiCommandType type) {
        return new ClaudeCuiCommand(type, "");
    }

    static ClaudeCuiCommand withContent(ClaudeCuiCommandType type, String content) {
        return new ClaudeCuiCommand(type, content);
    }
}
