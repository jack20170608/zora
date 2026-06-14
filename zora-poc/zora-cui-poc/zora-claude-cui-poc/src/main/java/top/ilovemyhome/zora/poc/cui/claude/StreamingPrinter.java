package top.ilovemyhome.zora.poc.cui.claude;

import java.io.PrintWriter;

/** Prints assistant text with a small configurable delay to mimic token streaming. */
final class StreamingPrinter {

    private static final String FIRST_LINE_PREFIX = "claude > ";
    private static final String NEXT_LINE_PREFIX = "         ";

    private final PrintWriter writer;
    private final long delayMillis;

    StreamingPrinter(PrintWriter writer, long delayMillis) {
        if (writer == null) {
            throw new IllegalArgumentException("writer must not be null");
        }
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must not be negative");
        }
        this.writer = writer;
        this.delayMillis = delayMillis;
    }

    StreamingPrinter withDelayMillis(long delayMillis) {
        return new StreamingPrinter(writer, delayMillis);
    }

    void printAssistantMessage(String message) {
        String safeMessage = message == null ? "" : message;
        String[] lines = safeMessage.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String prefix = index == 0 ? FIRST_LINE_PREFIX : NEXT_LINE_PREFIX;
            printSlowly(prefix + lines[index]);
            writer.println();
        }
        writer.flush();
    }

    private void printSlowly(String text) {
        for (int index = 0; index < text.length(); index++) {
            writer.print(text.charAt(index));
            writer.flush();
            sleepIfNeeded();
        }
    }

    private void sleepIfNeeded() {
        if (delayMillis == 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
