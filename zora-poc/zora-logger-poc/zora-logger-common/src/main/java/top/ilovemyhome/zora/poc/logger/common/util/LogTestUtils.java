package top.ilovemyhome.zora.poc.logger.common.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared utility methods for log-file assertions and test helpers.
 *
 * <p>These helpers are intended to be used across multiple logger POC sub-modules
 * to reduce duplication of file-reading and assertion logic.</p>
 */
public final class LogTestUtils {

    private LogTestUtils() {
        // utility class
    }

    /**
     * Reads all lines from the given log file using UTF-8 encoding.
     *
     * @param logFile the path to the log file
     * @return list of lines; never {@code null}
     * @throws UncheckedIOException if the file cannot be read
     */
    public static List<String> readLines(Path logFile) {
        try {
            return Files.readAllLines(logFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read log file: " + logFile, e);
        }
    }

    /**
     * Asserts that the log file contains at least one line matching each expected substring.
     *
     * @param logFile   the path to the log file
     * @param expected  substrings that must appear somewhere in the file
     * @throws AssertionError if any expected substring is missing
     */
    public static void assertLogFileContains(Path logFile, String... expected) {
        List<String> lines = readLines(logFile);
        for (String exp : expected) {
            boolean found = lines.stream().anyMatch(l -> l.contains(exp));
            if (!found) {
                throw new AssertionError(
                    "Log file " + logFile + " does not contain expected text: " + exp);
            }
        }
    }

    /**
     * Asserts that the log file contains exactly the expected number of lines.
     *
     * @param logFile      the path to the log file
     * @param expectedSize the expected line count
     * @throws AssertionError if the line count does not match
     */
    public static void assertLogFileLineCount(Path logFile, int expectedSize) {
        List<String> lines = readLines(logFile);
        if (lines.size() != expectedSize) {
            throw new AssertionError(
                "Expected log file " + logFile + " to have " + expectedSize
                    + " lines but was " + lines.size());
        }
    }

    /**
     * Safely deletes all regular files in the given directory.
     *
     * <p>Non-existent directories are ignored. Sub-directories are not touched.</p>
     *
     * @param dir the directory to clean
     */
    public static void cleanLogDirectory(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clean directory: " + dir, e);
        }
    }
}
