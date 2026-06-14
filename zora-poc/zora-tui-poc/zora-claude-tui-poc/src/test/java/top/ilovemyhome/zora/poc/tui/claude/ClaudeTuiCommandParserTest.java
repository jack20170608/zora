package top.ilovemyhome.zora.poc.tui.claude;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClaudeTuiCommandParserTest {

    private final ClaudeTuiCommandParser parser = new ClaudeTuiCommandParser();

    @Test
    void returnsEmptyCommandWhenInputIsBlank() {
        ClaudeTuiCommand command = parser.parse("   ");

        assertThat(command.type()).isEqualTo(ClaudeTuiCommandType.EMPTY);
        assertThat(command.content()).isEmpty();
    }

    @Test
    void parsesHelpCommand() {
        ClaudeTuiCommand command = parser.parse(":help");

        assertThat(command.type()).isEqualTo(ClaudeTuiCommandType.HELP);
        assertThat(command.content()).isEmpty();
    }

    @Test
    void parsesClearCommand() {
        ClaudeTuiCommand command = parser.parse(":clear");

        assertThat(command.type()).isEqualTo(ClaudeTuiCommandType.CLEAR);
        assertThat(command.content()).isEmpty();
    }

    @Test
    void parsesExitAliases() {
        assertThat(parser.parse(":exit").type()).isEqualTo(ClaudeTuiCommandType.EXIT);
        assertThat(parser.parse(":quit").type()).isEqualTo(ClaudeTuiCommandType.EXIT);
    }

    @Test
    void parsesConfigAliases() {
        assertThat(parser.parse(":config").type()).isEqualTo(ClaudeTuiCommandType.CONFIG);
        assertThat(parser.parse("/config").type()).isEqualTo(ClaudeTuiCommandType.CONFIG);
    }

    @Test
    void treatsOtherSlashInputAsChatText() {
        ClaudeTuiCommand command = parser.parse("/hello");

        assertThat(command.type()).isEqualTo(ClaudeTuiCommandType.CHAT);
        assertThat(command.content()).isEqualTo("/hello");
    }

    @Test
    void parsesUnknownColonCommand() {
        ClaudeTuiCommand command = parser.parse(":unknown");

        assertThat(command.type()).isEqualTo(ClaudeTuiCommandType.UNKNOWN);
        assertThat(command.content()).isEqualTo(":unknown");
    }

    @Test
    void parsesChatTextAndTrimsOuterWhitespace() {
        ClaudeTuiCommand command = parser.parse("  hello claude  ");

        assertThat(command.type()).isEqualTo(ClaudeTuiCommandType.CHAT);
        assertThat(command.content()).isEqualTo("hello claude");
    }
}
