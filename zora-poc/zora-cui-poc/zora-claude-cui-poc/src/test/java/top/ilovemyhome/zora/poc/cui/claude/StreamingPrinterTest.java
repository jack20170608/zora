package top.ilovemyhome.zora.poc.cui.claude;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class StreamingPrinterTest {

    @Test
    void printsPrefixAndContentWithoutDelay() {
        StringWriter output = new StringWriter();
        StreamingPrinter printer = new StreamingPrinter(new PrintWriter(output), 0L);

        printer.printAssistantMessage("hello");

        assertThat(output.toString()).isEqualTo("claude > hello" + System.lineSeparator());
    }

    @Test
    void indentsMultilineContent() {
        StringWriter output = new StringWriter();
        StreamingPrinter printer = new StreamingPrinter(new PrintWriter(output), 0L);

        printer.printAssistantMessage("line one" + System.lineSeparator() + "line two");

        assertThat(output.toString())
            .isEqualTo("claude > line one" + System.lineSeparator()
                + "         line two" + System.lineSeparator());
    }

    @Test
    void createsCopyWithUpdatedDelay() {
        StringWriter output = new StringWriter();
        StreamingPrinter original = new StreamingPrinter(new PrintWriter(output), 0L);

        StreamingPrinter updated = original.withDelayMillis(0L);
        updated.printAssistantMessage("copied");

        assertThat(updated).isNotSameAs(original);
        assertThat(output.toString()).isEqualTo("claude > copied" + System.lineSeparator());
    }
}
