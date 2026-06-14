package top.ilovemyhome.zora.poc.tui.claude;

/** Parsed user input with a command type and optional content. */
record ClaudeTuiCommand(ClaudeTuiCommandType type, String content) {

    ClaudeTuiCommand {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        content = content == null ? "" : content;
    }

    static ClaudeTuiCommand of(ClaudeTuiCommandType type) {
        return new ClaudeTuiCommand(type, "");
    }

    static ClaudeTuiCommand withContent(ClaudeTuiCommandType type, String content) {
        return new ClaudeTuiCommand(type, content);
    }
}
