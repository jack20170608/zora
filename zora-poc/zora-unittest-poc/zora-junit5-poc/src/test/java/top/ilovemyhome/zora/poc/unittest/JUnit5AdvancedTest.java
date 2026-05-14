package top.ilovemyhome.zora.poc.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Exploration of JUnit 5 parameterized and dynamic tests.
 */
class JUnit5AdvancedTest {

    private static final Logger LOG = LoggerFactory.getLogger(JUnit5AdvancedTest.class);

    @ParameterizedTest
    @ValueSource(strings = {"hello", "world", "junit"})
    @DisplayName("Parameterized test with @ValueSource")
    void parameterizedWithValueSource(String word) {
        LOG.info("Testing word: {}", word);
        assertFalse(word.isEmpty());
        assertTrue(word.length() > 2);
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1, 2",
        "2, 3, 5",
        "10, 20, 30"
    })
    @DisplayName("Parameterized test with @CsvSource")
    void parameterizedWithCsvSource(int a, int b, int expected) {
        assertEquals(expected, a + b);
    }

    @ParameterizedTest
    @MethodSource("provideStringsForTesting")
    @DisplayName("Parameterized test with @MethodSource")
    void parameterizedWithMethodSource(String input, boolean expectedStartsWithA) {
        assertEquals(expectedStartsWithA, input.toLowerCase().startsWith("a"));
    }

    static Stream<Arguments> provideStringsForTesting() {
        return Stream.of(
            Arguments.of("apple", true),
            Arguments.of("banana", false),
            Arguments.of("avocado", true)
        );
    }

    @TestFactory
    @DisplayName("Dynamic test factory")
    Stream<DynamicTest> dynamicTests() {
        return Stream.of("A", "B", "C")
            .map(text -> dynamicTest("Test " + text, () -> {
                LOG.info("Dynamic test for: {}", text);
                assertNotNull(text);
                assertEquals(1, text.length());
            }));
    }
}
