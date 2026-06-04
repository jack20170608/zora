package top.ilovemyhome.zora.poc.unittest;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exploration of JUnit 5 basic features: lifecycle, assertions, and tagging.
 */
class JUnit5BasicTest {

    private static final Logger LOG = LoggerFactory.getLogger(JUnit5BasicTest.class);

    @BeforeAll
    static void beforeAll() {
        LOG.info("@BeforeAll - runs once before all tests");
    }

    @AfterAll
    static void afterAll() {
        LOG.info("@AfterAll - runs once after all tests");
    }

    @BeforeEach
    void beforeEach() {
        LOG.info("@BeforeEach - runs before each test");
    }

    @AfterEach
    void afterEach() {
        LOG.info("@AfterEach - runs after each test");
    }

    @Test
    @DisplayName("Basic assertion demo")
    void basicAssertions() {
        assertEquals(4, 2 + 2, "Basic math should work");
        assertTrue("hello".startsWith("he"));
        assertNull(null);
        assertNotNull(new Object());
    }

    @Test
    @Disabled("This test is disabled for demonstration purposes")
    void disabledTest() {
        fail("Should never run");
    }

    @RepeatedTest(3)
    @DisplayName("Repeated test demo")
    void repeatedTest(RepetitionInfo info) {
        LOG.info("Running repetition {} of {}", info.getCurrentRepetition(), info.getTotalRepetitions());
        assertTrue(info.getCurrentRepetition() <= info.getTotalRepetitions());
    }

    @Nested
    @DisplayName("Nested test class demo")
    class NestedTests {

        @Test
        @DisplayName("Nested test case")
        void nestedTest() {
            assertEquals("nested", "nested");
        }
    }
}
