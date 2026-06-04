package top.ilovemyhome.zora.poc.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Exploration of AssertJ fluent assertion features.
 */
class AssertJExploreTest {

    @Test
    @DisplayName("Basic fluent assertions")
    void basicAssertions() {
        assertThat("hello")
            .isNotEmpty()
            .startsWith("he")
            .hasSize(5);

        assertThat(42)
            .isPositive()
            .isGreaterThan(40)
            .isLessThan(50);
    }

    @Test
    @DisplayName("Exception assertions")
    void exceptionAssertions() {
        assertThatThrownBy(() -> {
            throw new IllegalArgumentException("invalid input");
        })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid");

        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> {
                String s = null;
                s.length();
            })
            .withMessageContaining("null");
    }

    @Test
    @DisplayName("Collection assertions")
    void collectionAssertions() {
        List<String> fruits = List.of("apple", "banana", "cherry");

        assertThat(fruits)
            .hasSize(3)
            .contains("banana")
            .containsExactly("apple", "banana", "cherry")
            .doesNotContain("grape");
    }

    @Test
    @DisplayName("Object field assertions")
    void objectAssertions() {
        record Person(String name, int age) {}

        Person person = new Person("Alice", 30);

        assertThat(person)
            .extracting(Person::name, Person::age)
            .containsExactly("Alice", 30);
    }
}
