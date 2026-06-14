package top.ilovemyhome.zora.poc.tui.claude;

/** Types of input supported by the Claude-like TUI shell. */
enum ClaudeTuiCommandType {
    HELP,
    CLEAR,
    CONFIG,
    EXIT,
    CHAT,
    EMPTY,
    UNKNOWN
}
