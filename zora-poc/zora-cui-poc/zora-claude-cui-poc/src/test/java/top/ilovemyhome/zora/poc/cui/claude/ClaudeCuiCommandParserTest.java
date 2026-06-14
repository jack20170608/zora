package top.ilovemyhome.zora.poc.cui.claude;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClaudeCuiCommandParserTest {

    private final ClaudeCuiCommandParser parser = new ClaudeCuiCommandParser();

    @Test
    void returnsEmptyCommandWhenInputIsBlank() {
        ClaudeCuiCommand command = parser.parse("   ");

        assertThat(command.type()).isEqualTo(ClaudeCuiCommandType.EMPTY);
        assertThat(command.content()).isEmpty();
    }

    @Test
    void parsesHelpCommand() {
        ClaudeCuiCommand command = parser.parse(":help");

        assertThat(command.type()).isEqualTo(ClaudeCuiCommandType.HELP);
        assertThat(command.content()).isEmpty();
    }

    @Test
    void parsesClearCommand() {
        ClaudeCuiCommand command = parser.parse(":clear");

        assertThat(command.type()).isEqualTo(ClaudeCuiCommandType.CLEAR);
        assertThat(command.content()).isEmpty();
    }

    @Test
    void parsesExitAliases() {
        assertThat(parser.parse(":exit").type()).isEqualTo(ClaudeCuiCommandType.EXIT);
        assertThat(parser.parse(":quit").type()).isEqualTo(ClaudeCuiCommandType.EXIT);
    }

    @Test
    void parsesUnknownColonCommand() {
        ClaudeCuiCommand command = parser.parse(":unknown");

        assertThat(command.type()).isEqualTo(ClaudeCuiCommandType.UNKNOWN);
        assertThat(command.content()).isEqualTo(":unknown");
    }

    @Test
    void parsesChatTextAndTrimsOuterWhitespace() {
        ClaudeCuiCommand command = parser.parse("  hello claude  ");

        assertThat(command.type()).isEqualTo(ClaudeCuiCommandType.CHAT);
        assertThat(command.content()).isEqualTo("hello claude");
    }
}
