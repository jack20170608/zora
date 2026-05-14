# zora-unittest-poc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a new POC aggregator module `zora-unittest-poc` under `zora-poc` with a single leaf module `zora-junit5-poc` that contains exploratory test code for JUnit 5, AssertJ, and Mockito.

**Architecture:** Follow the existing three-level aggregator pattern (`zora-poc` -> aggregator -> leaf). The aggregator `zora-unittest-poc` has no code; the leaf `zora-junit5-poc` contains all exploratory tests in `src/test/java`. All third-party dependencies are version-managed by `zora-dependencies` (junit-bom, mockito-bom, assertj-bom, slf4j-simple).

**Tech Stack:** Maven, Java 25, JUnit 5.14.4, AssertJ 3.27.7, Mockito 5.23.0, SLF4J Simple

---

### File Map

| File | Action | Purpose |
|---|---|---|
| `zora-poc/pom.xml` | Modify | Add `<module>zora-unittest-poc</module>` |
| `zora-poc/zora-unittest-poc/pom.xml` | Create | Aggregator POM, references `zora-parent`, lists `zora-junit5-poc` as child module |
| `zora-poc/zora-unittest-poc/metadata/metadata.json` | Create | Module metadata with Maven property placeholders |
| `zora-poc/zora-unittest-poc/README.md` | Create | Chinese description of the aggregator's purpose |
| `zora-poc/zora-unittest-poc/src/main/resources/` | Create (empty dir) | Per CLAUDE.md convention |
| `zora-poc/zora-unittest-poc/src/test/resources/` | Create (empty dir) | Per CLAUDE.md convention |
| `zora-poc/zora-unittest-poc/zora-junit5-poc/pom.xml` | Create | Leaf module POM with JUnit 5 + AssertJ + Mockito + SLF4J Simple (all `test` scope) |
| `zora-poc/zora-unittest-poc/zora-junit5-poc/metadata/metadata.json` | Create | Module metadata with Maven property placeholders |
| `zora-poc/zora-unittest-poc/zora-junit5-poc/README.md` | Create | Chinese description of the leaf module |
| `zora-poc/zora-unittest-poc/zora-junit5-poc/src/main/resources/` | Create (empty dir) | Per CLAUDE.md convention |
| `zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/resources/simplelogger.properties` | Create | SLF4J Simple logger configuration |
| `zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/java/.../JUnit5BasicTest.java` | Create | Basic JUnit 5 tests (lifecycle, assertions) |
| `zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/java/.../AssertJExploreTest.java` | Create | AssertJ assertion exploration tests |
| `zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/java/.../MockitoExploreTest.java` | Create | Mockito mock/spy/verify exploration tests |

---

### Task 1: Update zora-poc aggregator to include new module

**Files:**
- Modify: `zora-poc/pom.xml`

- [ ] **Step 1: Add module declaration**

In `zora-poc/pom.xml`, inside the `<modules>` element, add `zora-unittest-poc` alongside the existing `zora-java-logger-poc`:

```xml
    <modules>
        <module>zora-java-logger-poc</module>
        <module>zora-unittest-poc</module>
    </modules>
```

- [ ] **Step 2: Verify syntax**

No test to run here; just confirm the XML is well-formed.

- [ ] **Step 3: Commit**

```bash
git add zora-poc/pom.xml
git commit -m "feat: add zora-unittest-poc to zora-poc aggregator modules"
```

---

### Task 2: Create zora-unittest-poc aggregator module

**Files:**
- Create: `zora-poc/zora-unittest-poc/pom.xml`
- Create: `zora-poc/zora-unittest-poc/metadata/metadata.json`
- Create: `zora-poc/zora-unittest-poc/README.md`
- Create: `zora-poc/zora-unittest-poc/src/main/resources/` (empty)
- Create: `zora-poc/zora-unittest-poc/src/test/resources/` (empty)

- [ ] **Step 1: Write aggregator POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>top.ilovemyhome.zora</groupId>
        <artifactId>zora-parent</artifactId>
        <version>${revision}</version>
        <relativePath>../../zora-parent/pom.xml</relativePath>
    </parent>

    <artifactId>zora-unittest-poc</artifactId>
    <packaging>pom</packaging>
    <name>zora-unittest-poc - Unit Test Framework Exploration</name>
    <description>Aggregator POC module for exploring Java unit testing frameworks.
        All code lives in test scope and is not packaged into production artifacts.
    </description>

    <modules>
        <module>zora-junit5-poc</module>
    </modules>

    <build>
        <resources>
            <resource>
                <directory>metadata</directory>
                <filtering>true</filtering>
            </resource>
            <resource>
                <directory>src/main/resources</directory>
            </resource>
        </resources>
        <testResources>
            <testResource>
                <directory>src/test/resources</directory>
            </testResource>
        </testResources>
    </build>

</project>
```

- [ ] **Step 2: Create metadata.json**

```json
{
  "groupId": "@project.groupId@",
  "artifactId": "@project.artifactId@",
  "description": "@project.description@",
  "version": "@project.version@",
  "scmUrl": "@project.scmUrl@"
}
```

- [ ] **Step 3: Create README.md**

```markdown
# zora-unittest-poc

单元测试框架预研聚合模块。

## 目的

本模块用于聚合各类 Java 单元测试框架的 POC 子模块，所有代码均在 `test` scope 下运行，不会被打包到生产产物中。

## 子模块

- `zora-junit5-poc` — JUnit 5、AssertJ、Mockito 功能与特性预研

## 运行测试

```bash
mvn test -pl zora-junit5-poc
```

或从项目根目录：

```bash
mvn test -pl zora-poc/zora-unittest-poc/zora-junit5-poc
```
```

- [ ] **Step 4: Create empty resource directories**

```bash
mkdir -p zora-poc/zora-unittest-poc/src/main/resources
mkdir -p zora-poc/zora-unittest-poc/src/test/resources
```

- [ ] **Step 5: Commit**

```bash
git add zora-poc/zora-unittest-poc/
git commit -m "feat: add zora-unittest-poc aggregator module"
```

---

### Task 3: Create zora-junit5-poc leaf module (POM and metadata)

**Files:**
- Create: `zora-poc/zora-unittest-poc/zora-junit5-poc/pom.xml`
- Create: `zora-poc/zora-unittest-poc/zora-junit5-poc/metadata/metadata.json`
- Create: `zora-poc/zora-unittest-poc/zora-junit5-poc/README.md`
- Create: `zora-poc/zora-unittest-poc/zora-junit5-poc/src/main/resources/` (empty)
- Create: `zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/resources/simplelogger.properties`

- [ ] **Step 1: Write leaf module POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>top.ilovemyhome.zora</groupId>
        <artifactId>zora-unittest-poc</artifactId>
        <version>${revision}</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>zora-junit5-poc</artifactId>
    <name>zora-junit5-poc - JUnit 5 Exploration</name>
    <description>POC sub-module for exploring JUnit 5, AssertJ, and Mockito features.
        All code lives in test scope and is not packaged into production artifacts.
    </description>

    <dependencies>
        <!-- JUnit 5 -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-params</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- AssertJ -->
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Mockito -->
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Logging for tests -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <resources>
            <resource>
                <directory>metadata</directory>
                <filtering>true</filtering>
            </resource>
            <resource>
                <directory>src/main/resources</directory>
            </resource>
        </resources>
        <testResources>
            <testResource>
                <directory>src/test/resources</directory>
            </testResource>
        </testResources>
    </build>

</project>
```

- [ ] **Step 2: Create metadata.json**

```json
{
  "groupId": "@project.groupId@",
  "artifactId": "@project.artifactId@",
  "description": "@project.description@",
  "version": "@project.version@",
  "scmUrl": "@project.scmUrl@"
}
```

- [ ] **Step 3: Create README.md**

```markdown
# zora-junit5-poc

JUnit 5、AssertJ、Mockito 功能与特性预研子模块。

## 目的

本模块通过测试用例形式，对以下三个主流 Java 单元测试框架进行功能和特性预研，所有代码均在 `test` scope 下运行。

## 预研范围

### JUnit 5
- 基本测试注解（@Test, @BeforeEach, @AfterEach, @BeforeAll, @AfterAll）
- 参数化测试（@ParameterizedTest）
- 动态测试（@TestFactory）
- 嵌套测试（@Nested）

### AssertJ
- 流式基本断言
- 异常断言
- 集合断言

### Mockito
- Mock 与 Stub
- 验证调用
- Spy 与注解用法

## 运行测试

```bash
mvn test -pl zora-junit5-poc
```

或从项目根目录：

```bash
mvn test -pl zora-poc/zora-unittest-poc/zora-junit5-poc
```
```

- [ ] **Step 4: Create resource directories and simplelogger.properties**

```bash
mkdir -p zora-poc/zora-unittest-poc/zora-junit5-poc/src/main/resources
mkdir -p zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/resources
```

Create `zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/resources/simplelogger.properties`:

```properties
# Configure slf4j simple logger for testing
org.slf4j.simpleLogger.defaultLogLevel = info
org.slf4j.simpleLogger.logFile = System.out
org.slf4j.simpleLogger.showDateTime = true
org.slf4j.simpleLogger.dateTimeFormat = yyyy-MM-dd HH:mm:ss.SSS
org.slf4j.simpleLogger.showThreadName = true
org.slf4j.simpleLogger.showLogName = true
org.slf4j.simpleLogger.levelInBrackets = true
```

- [ ] **Step 5: Commit**

```bash
git add zora-poc/zora-unittest-poc/zora-junit5-poc/
git commit -m "feat: add zora-junit5-poc leaf module with JUnit 5, AssertJ, Mockito dependencies"
```

---

### Task 4: Write JUnit 5 exploratory tests

**Files:**
- Create: `zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/java/top/ilovemyhome/zora/poc/unittest/JUnit5BasicTest.java`

- [ ] **Step 1: Write the test class**

```java
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
```

- [ ] **Step 2: Run the tests**

```bash
cd zora-poc/zora-unittest-poc/zora-junit5-poc && mvn test -Dtest=JUnit5BasicTest
```

Expected: All tests pass except the disabled one (which is skipped). Check the console output for `@BeforeAll`, `@BeforeEach`, `@AfterEach`, `@AfterAll` log lines.

- [ ] **Step 3: Commit**

```bash
git add zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/java/
git commit -m "feat: add JUnit 5 basic feature exploration tests"
```

---

### Task 5: Write parameterized and dynamic tests

**Files:**
- Create: `zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/java/top/ilovemyhome/zora/poc/unittest/JUnit5AdvancedTest.java`

- [ ] **Step 1: Write the test class**

```java
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
```

- [ ] **Step 2: Run the tests**

```bash
cd zora-poc/zora-unittest-poc/zora-junit5-poc && mvn test -Dtest=JUnit5AdvancedTest
```

Expected: All parameterized and dynamic tests pass.

- [ ] **Step 3: Commit**

```bash
git add zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/java/
git commit -m "feat: add JUnit 5 parameterized and dynamic test exploration"
```

---

### Task 6: Write AssertJ exploratory tests

**Files:**
- Create: `zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/java/top/ilovemyhome/zora/poc/unittest/AssertJExploreTest.java`

- [ ] **Step 1: Write the test class**

```java
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
```

- [ ] **Step 2: Run the tests**

```bash
cd zora-poc/zora-unittest-poc/zora-junit5-poc && mvn test -Dtest=AssertJExploreTest
```

Expected: All AssertJ tests pass.

- [ ] **Step 3: Commit**

```bash
git add zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/java/
git commit -m "feat: add AssertJ fluent assertion exploration tests"
```

---

### Task 7: Write Mockito exploratory tests

**Files:**
- Create: `zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/java/top/ilovemyhome/zora/poc/unittest/MockitoExploreTest.java`

- [ ] **Step 1: Write the test class**

```java
package top.ilovemyhome.zora.poc.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exploration of Mockito mocking and verification features.
 */
@ExtendWith(MockitoExtension.class)
class MockitoExploreTest {

    private static final Logger LOG = LoggerFactory.getLogger(MockitoExploreTest.class);

    interface UserService {
        String getUserName(int userId);
        boolean isActive(int userId);
    }

    @Mock
    private UserService userService;

    @BeforeEach
    void setUp() {
        LOG.info("Setting up mocks");
    }

    @Test
    @DisplayName("Basic mock and stub")
    void basicMockAndStub() {
        when(userService.getUserName(1)).thenReturn("Alice");
        when(userService.getUserName(2)).thenReturn("Bob");

        assertThat(userService.getUserName(1)).isEqualTo("Alice");
        assertThat(userService.getUserName(2)).isEqualTo("Bob");
    }

    @Test
    @DisplayName("Argument matchers")
    void argumentMatchers() {
        when(userService.getUserName(anyInt())).thenReturn("Unknown");
        when(userService.getUserName(eq(42))).thenReturn("The Answer");

        assertThat(userService.getUserName(99)).isEqualTo("Unknown");
        assertThat(userService.getUserName(42)).isEqualTo("The Answer");
    }

    @Test
    @DisplayName("Verify interactions")
    void verifyInteractions() {
        userService.getUserName(1);
        userService.getUserName(1);
        userService.isActive(1);

        verify(userService, times(2)).getUserName(1);
        verify(userService, atLeastOnce()).isActive(anyInt());
        verify(userService, never()).getUserName(999);
    }

    @Test
    @DisplayName("Spy on real object")
    void spyDemo() {
        List<String> realList = new java.util.ArrayList<>();
        realList.add("one");

        List<String> spyList = spy(realList);

        assertThat(spyList.size()).isEqualTo(1);

        when(spyList.size()).thenReturn(100);
        assertThat(spyList.size()).isEqualTo(100);

        spyList.add("two");
        verify(spyList).add("two");
    }

    @Test
    @DisplayName("Mock with @InjectMocks")
    void injectMocksDemo() {
        // Demonstrate that @Mock annotation is properly initialized by the extension
        assertThat(userService).isNotNull();
        when(userService.isActive(anyInt())).thenReturn(true);
        assertThat(userService.isActive(1)).isTrue();
    }
}
```

- [ ] **Step 2: Run the tests**

```bash
cd zora-poc/zora-unittest-poc/zora-junit5-poc && mvn test -Dtest=MockitoExploreTest
```

Expected: All Mockito tests pass.

- [ ] **Step 3: Commit**

```bash
git add zora-poc/zora-unittest-poc/zora-junit5-poc/src/test/java/
git commit -m "feat: add Mockito mock, stub, verify and spy exploration tests"
```

---

### Task 8: Full module build verification

- [ ] **Step 1: Build the entire new module path from root**

```bash
cd D:/project/nas_gogs/zora && mvn clean test -pl zora-poc/zora-unittest-poc/zora-junit5-poc -am
```

Expected: Build succeeds, all tests in `JUnit5BasicTest`, `JUnit5AdvancedTest`, `AssertJExploreTest`, `MockitoExploreTest` pass.

- [ ] **Step 2: Commit (if any uncommitted changes)**

```bash
git add -A
git commit -m "feat: complete zora-unittest-poc with JUnit 5, AssertJ, Mockito exploration tests" || echo "Nothing to commit"
```

---

## Spec Coverage Check

| Spec Requirement | Task |
|---|---|
| zora-poc 添加 zora-unittest-poc 模块 | Task 1 |
| 创建 zora-unittest-poc 聚合模块 (pom + metadata + README + resources) | Task 2 |
| 创建 zora-junit5-poc 叶子模块 (pom + metadata + README + resources + simplelogger.properties) | Task 3 |
| JUnit 5 基本功能预研 (生命周期、断言、@Nested、@RepeatedTest) | Task 4 |
| JUnit 5 参数化和动态测试预研 | Task 5 |
| AssertJ 流式断言预研 | Task 6 |
| Mockito Mock/Stub/Verify/Spy 预研 | Task 7 |
| 全链路构建验证 | Task 8 |

## Placeholder Scan

- No "TBD", "TODO", or "implement later" found.
- All code blocks contain complete, compilable code.
- All file paths are exact and match the project structure.
