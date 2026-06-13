# zora-claude-cui-poc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a new `zora-poc/zora-cui-poc/zora-claude-cui-poc` Java POC module that builds and runs a JLine-based Claude-like terminal chat shell with mock assistant responses.

**Architecture:** Follow the existing `zora-poc` aggregator pattern: `zora-poc` registers a new `zora-cui-poc` aggregator, and the aggregator registers the executable leaf module `zora-claude-cui-poc`. The leaf module keeps JLine usage at the application boundary, separates command parsing, state, mock assistant response generation, and streaming output into focused Java classes, and uses unit tests for the pure logic.

**Tech Stack:** Maven, Java 25, JLine 3.30.6, JUnit 5.14.4, AssertJ 3.27.7, Mockito 5.23.0, SLF4J Simple

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `zora-dependencies/pom.xml` | Modify | Add managed `org.jline:jline` version for the POC dependency. |
| `zora-poc/pom.xml` | Modify | Add `<module>zora-cui-poc</module>` to the POC aggregator. |
| `zora-poc/zora-cui-poc/pom.xml` | Create | Aggregator POM for CUI POC modules. |
| `zora-poc/zora-cui-poc/metadata/metadata.json` | Create | Aggregator metadata with Maven placeholders. |
| `zora-poc/zora-cui-poc/README.md` | Create | Chinese README for the CUI POC aggregator. |
| `zora-poc/zora-cui-poc/src/main/resources/.gitkeep` | Create | Keep required main resources directory. |
| `zora-poc/zora-cui-poc/src/test/resources/.gitkeep` | Create | Keep required test resources directory. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/pom.xml` | Create | Leaf module POM with JLine, test dependencies, and exec plugin. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/metadata/metadata.json` | Create | Leaf module metadata with Maven placeholders. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/README.md` | Create | Chinese README with build/run instructions. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/resources/.gitkeep` | Create | Keep required main resources directory. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/resources/simplelogger.properties` | Create | Test logging configuration required by project instructions. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiApplication.java` | Create | Main entry point and JLine wiring. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiShell.java` | Create | Interactive shell loop and command dispatch. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommand.java` | Create | Immutable command parse result. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandType.java` | Create | Command type enum. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandParser.java` | Create | Parse colon commands and chat input. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ConversationState.java` | Create | Immutable session state. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/MockAssistant.java` | Create | Deterministic mock assistant responses. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/StreamingPrinter.java` | Create | Simulated streaming output. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandParserTest.java` | Create | Parser unit tests. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ConversationStateTest.java` | Create | State unit tests. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/MockAssistantTest.java` | Create | Mock assistant unit tests. |
| `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/StreamingPrinterTest.java` | Create | Streaming printer unit tests. |

---

### Task 1: Register JLine in dependency management

**Files:**
- Modify: `zora-dependencies/pom.xml`

- [ ] **Step 1: Add the JLine version property**

In `zora-dependencies/pom.xml`, add this property inside the existing `<properties>` block after `<rocksdb.version>9.10.0</rocksdb.version>`:

```xml
        <jline.version>3.30.6</jline.version>
```

The resulting property block tail should look like this:

```xml
        <jdbi3.version>3.53.0</jdbi3.version>
        <muserver.version>2.2.8</muserver.version>
        <rocksdb.version>9.10.0</rocksdb.version>
        <jline.version>3.30.6</jline.version>
    </properties>
```

- [ ] **Step 2: Add the managed dependency**

In `zora-dependencies/pom.xml`, add this dependency inside `<dependencyManagement><dependencies>` after the existing `rocksdbjni` managed dependency:

```xml

            <!-- JLine terminal interaction for CUI POCs -->
            <dependency>
                <groupId>org.jline</groupId>
                <artifactId>jline</artifactId>
                <version>${jline.version}</version>
            </dependency>
```

The surrounding tail should become:

```xml
            <!-- RocksDB JNI bindings (embedded KV/graph store) -->
            <dependency>
                <groupId>org.rocksdb</groupId>
                <artifactId>rocksdbjni</artifactId>
                <version>${rocksdb.version}</version>
            </dependency>

            <!-- JLine terminal interaction for CUI POCs -->
            <dependency>
                <groupId>org.jline</groupId>
                <artifactId>jline</artifactId>
                <version>${jline.version}</version>
            </dependency>

        </dependencies>
```

- [ ] **Step 3: Verify POM syntax**

Run:

```bash
mvn -pl zora-dependencies validate
```

Expected: `BUILD SUCCESS`.

If Maven cannot resolve `org.jline:jline:3.30.6`, replace the property with a Maven Central available JLine 3.x release and rerun the same command.

---

### Task 2: Register the CUI POC aggregator

**Files:**
- Modify: `zora-poc/pom.xml`

- [ ] **Step 1: Add the module declaration**

In `zora-poc/pom.xml`, add `zora-cui-poc` inside the existing `<modules>` block after `zora-persistence-poc`:

```xml
    <modules>
        <module>zora-logger-poc</module>
        <module>zora-test-poc</module>
        <module>zora-persistence-poc</module>
        <module>zora-cui-poc</module>
    </modules>
```

- [ ] **Step 2: Verify aggregator syntax**

Run:

```bash
mvn -pl zora-poc validate
```

Expected initially: Maven may fail because `zora-cui-poc` does not exist yet. If it fails with `Child module ... does not exist`, continue to Task 3. Any XML parse error must be fixed before continuing.

---

### Task 3: Create the zora-cui-poc aggregator module

**Files:**
- Create: `zora-poc/zora-cui-poc/pom.xml`
- Create: `zora-poc/zora-cui-poc/metadata/metadata.json`
- Create: `zora-poc/zora-cui-poc/README.md`
- Create: `zora-poc/zora-cui-poc/src/main/resources/.gitkeep`
- Create: `zora-poc/zora-cui-poc/src/test/resources/.gitkeep`

- [ ] **Step 1: Create the aggregator POM**

Create `zora-poc/zora-cui-poc/pom.xml` with exactly:

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

    <artifactId>zora-cui-poc</artifactId>
    <packaging>pom</packaging>
    <name>zora-cui-poc - Command-line User Interface Exploration</name>
    <description>Aggregator POC module for exploring Java command-line user interface technologies.</description>

    <modules>
        <module>zora-claude-cui-poc</module>
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

- [ ] **Step 2: Create aggregator metadata**

Create `zora-poc/zora-cui-poc/metadata/metadata.json` with exactly:

```json
{
  "groupId": "@project.groupId@",
  "artifactId": "@project.artifactId@",
  "description": "@project.description@",
  "version": "@project.version@",
  "scmUrl": "@project.scmUrl@"
}
```

- [ ] **Step 3: Create aggregator README**

Create `zora-poc/zora-cui-poc/README.md` with exactly:

```markdown
# zora-cui-poc

CUI（Command-line User Interface）技术预研聚合模块。

## 目的

本模块用于聚合 Java 终端交互、REPL、聊天式命令行界面等方向的 POC 子模块。当前包含 `zora-claude-cui-poc`，用于验证 Java + JLine 能否构建类似 Claude 的聊天式终端交互壳。

## 模块列表

| 模块 | 说明 |
|---|---|
| `zora-claude-cui-poc` | 使用 JLine 构建 Claude-like mock CUI。 |

## 开发约定

- POC 依赖应限制在本聚合模块或叶子模块中，不影响正式功能模块。
- 每个叶子模块应提供 README、metadata 和测试资源配置。
- 可运行 CUI POC 允许在 `src/main/java` 中提供入口类，便于验证真实启动体验。
```

- [ ] **Step 4: Create required resource directories**

Create these empty marker files so Git keeps the required resource directories:

```text
zora-poc/zora-cui-poc/src/main/resources/.gitkeep
zora-poc/zora-cui-poc/src/test/resources/.gitkeep
```

Both files should be empty.

- [ ] **Step 5: Verify aggregator after leaf is still missing**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc validate
```

Expected initially: Maven may fail because `zora-claude-cui-poc` does not exist yet. If it fails with `Child module ... does not exist`, continue to Task 4. Any XML parse error must be fixed before continuing.

---

### Task 4: Create the zora-claude-cui-poc leaf module skeleton

**Files:**
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/pom.xml`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/metadata/metadata.json`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/README.md`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/resources/.gitkeep`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/resources/simplelogger.properties`

- [ ] **Step 1: Create the leaf POM**

Create `zora-poc/zora-cui-poc/zora-claude-cui-poc/pom.xml` with exactly:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>top.ilovemyhome.zora</groupId>
        <artifactId>zora-cui-poc</artifactId>
        <version>${revision}</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>zora-claude-cui-poc</artifactId>
    <name>zora-claude-cui-poc - Claude-like CUI Exploration</name>
    <description>POC sub-module for exploring a Claude-like terminal chat shell with Java and JLine.</description>

    <dependencies>
        <!-- Terminal interaction -->
        <dependency>
            <groupId>org.jline</groupId>
            <artifactId>jline</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <scope>test</scope>
        </dependency>
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
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
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
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>top.ilovemyhome.zora.poc.cui.claude.ClaudeCuiApplication</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

- [ ] **Step 2: Create leaf metadata**

Create `zora-poc/zora-cui-poc/zora-claude-cui-poc/metadata/metadata.json` with exactly:

```json
{
  "groupId": "@project.groupId@",
  "artifactId": "@project.artifactId@",
  "description": "@project.description@",
  "version": "@project.version@",
  "scmUrl": "@project.scmUrl@"
}
```

- [ ] **Step 3: Create leaf README**

Create `zora-poc/zora-cui-poc/zora-claude-cui-poc/README.md` with exactly:

```markdown
# zora-claude-cui-poc

使用 Java + JLine 构建 Claude-like CUI 的 POC 子模块。

## 目标

本模块验证 Java 是否能够构建一个类似 Claude 的聊天式终端交互壳。当前版本仅使用 mock assistant，不接入真实 Claude / Anthropic API。

## 功能

- 彩色欢迎页和提示符
- JLine 输入历史
- `:help`、`:clear`、`:exit`、`:quit` 内置命令
- mock assistant 流式输出效果
- 简单会话轮次状态

## 构建

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc test
```

## 运行

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc exec:java
```

启动后输入普通文本进行 mock 对话，输入 `:help` 查看命令，输入 `:exit` 退出。

## 说明

本模块不读取 API Key，不调用真实 LLM，也不产生外部网络请求。后续如果需要接入真实 Claude API，应新增独立客户端抽象，并使用官方 Anthropic Java SDK。
```

- [ ] **Step 4: Create resource files**

Create empty file:

```text
zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/resources/.gitkeep
```

Create `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/resources/simplelogger.properties` with exactly:

```properties
# Configure slf4j simple logger for testing
org.slf4j.simpleLogger.defaultLogLevel = info
org.slf4j.simpleLogger.logFile = System.out
org.slf4j.simpleLogger.showDateTime = true
# 设置日期时间格式
org.slf4j.simpleLogger.dateTimeFormat =yyyy-MM-dd HH:mm:ss.SSS
# 显示线程名称
org.slf4j.simpleLogger.showThreadName = true
org.slf4j.simpleLogger.showLogName = true
# 日志级别显示在方括号内
org.slf4j.simpleLogger.levelInBrackets = true
```

- [ ] **Step 5: Verify skeleton POMs**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc validate
```

Expected: `BUILD SUCCESS`. If the build fails because the main class does not exist, continue to Task 5 before running tests.

---

### Task 5: Implement command parsing with tests first

**Files:**
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandParserTest.java`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandType.java`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommand.java`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiCommandParser.java`

- [ ] **Step 1: Write the failing parser tests**

Create `ClaudeCuiCommandParserTest.java` with exactly:

```java
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ClaudeCuiCommandParserTest test
```

Expected: compilation failure because `ClaudeCuiCommandParser`, `ClaudeCuiCommand`, and `ClaudeCuiCommandType` do not exist.

- [ ] **Step 3: Implement command type enum**

Create `ClaudeCuiCommandType.java` with exactly:

```java
package top.ilovemyhome.zora.poc.cui.claude;

/** Types of input supported by the Claude-like CUI shell. */
enum ClaudeCuiCommandType {
    HELP,
    CLEAR,
    EXIT,
    CHAT,
    EMPTY,
    UNKNOWN
}
```

- [ ] **Step 4: Implement immutable command result**

Create `ClaudeCuiCommand.java` with exactly:

```java
package top.ilovemyhome.zora.poc.cui.claude;

/** Parsed user input with a command type and optional content. */
record ClaudeCuiCommand(ClaudeCuiCommandType type, String content) {

    ClaudeCuiCommand {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        content = content == null ? "" : content;
    }

    static ClaudeCuiCommand of(ClaudeCuiCommandType type) {
        return new ClaudeCuiCommand(type, "");
    }

    static ClaudeCuiCommand withContent(ClaudeCuiCommandType type, String content) {
        return new ClaudeCuiCommand(type, content);
    }
}
```

- [ ] **Step 5: Implement parser**

Create `ClaudeCuiCommandParser.java` with exactly:

```java
package top.ilovemyhome.zora.poc.cui.claude;

/** Parses raw terminal input into shell commands or chat messages. */
final class ClaudeCuiCommandParser {

    ClaudeCuiCommand parse(String input) {
        String normalizedInput = input == null ? "" : input.trim();
        if (normalizedInput.isEmpty()) {
            return ClaudeCuiCommand.of(ClaudeCuiCommandType.EMPTY);
        }
        return switch (normalizedInput) {
            case ":help" -> ClaudeCuiCommand.of(ClaudeCuiCommandType.HELP);
            case ":clear" -> ClaudeCuiCommand.of(ClaudeCuiCommandType.CLEAR);
            case ":exit", ":quit" -> ClaudeCuiCommand.of(ClaudeCuiCommandType.EXIT);
            default -> parseDefault(normalizedInput);
        };
    }

    private ClaudeCuiCommand parseDefault(String normalizedInput) {
        if (normalizedInput.startsWith(":")) {
            return ClaudeCuiCommand.withContent(ClaudeCuiCommandType.UNKNOWN, normalizedInput);
        }
        return ClaudeCuiCommand.withContent(ClaudeCuiCommandType.CHAT, normalizedInput);
    }
}
```

- [ ] **Step 6: Run parser tests to verify they pass**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ClaudeCuiCommandParserTest test
```

Expected: `Tests run: 6, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

---

### Task 6: Implement immutable conversation state with tests first

**Files:**
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/ConversationStateTest.java`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ConversationState.java`

- [ ] **Step 1: Write failing state tests**

Create `ConversationStateTest.java` with exactly:

```java
package top.ilovemyhome.zora.poc.cui.claude;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConversationStateTest {

    @Test
    void startsWithZeroTurnsAndRunningStatus() {
        ConversationState state = ConversationState.initial();

        assertThat(state.turnCount()).isZero();
        assertThat(state.shouldExit()).isFalse();
    }

    @Test
    void incrementsTurnWithoutMutatingOriginalState() {
        ConversationState original = ConversationState.initial();
        ConversationState updated = original.nextTurn();

        assertThat(original.turnCount()).isZero();
        assertThat(updated.turnCount()).isEqualTo(1);
        assertThat(updated.shouldExit()).isFalse();
    }

    @Test
    void marksExitWithoutMutatingOriginalState() {
        ConversationState original = ConversationState.initial();
        ConversationState updated = original.exit();

        assertThat(original.shouldExit()).isFalse();
        assertThat(updated.shouldExit()).isTrue();
        assertThat(updated.turnCount()).isZero();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ConversationStateTest test
```

Expected: compilation failure because `ConversationState` does not exist.

- [ ] **Step 3: Implement immutable state**

Create `ConversationState.java` with exactly:

```java
package top.ilovemyhome.zora.poc.cui.claude;

/** Immutable shell conversation state. */
record ConversationState(int turnCount, boolean shouldExit) {

    ConversationState {
        if (turnCount < 0) {
            throw new IllegalArgumentException("turnCount must not be negative");
        }
    }

    static ConversationState initial() {
        return new ConversationState(0, false);
    }

    ConversationState nextTurn() {
        return new ConversationState(turnCount + 1, shouldExit);
    }

    ConversationState exit() {
        return new ConversationState(turnCount, true);
    }
}
```

- [ ] **Step 4: Run state tests to verify they pass**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=ConversationStateTest test
```

Expected: `Tests run: 3, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

---

### Task 7: Implement mock assistant with tests first

**Files:**
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/MockAssistantTest.java`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/MockAssistant.java`

- [ ] **Step 1: Write failing mock assistant tests**

Create `MockAssistantTest.java` with exactly:

```java
package top.ilovemyhome.zora.poc.cui.claude;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockAssistantTest {

    private final MockAssistant assistant = new MockAssistant();

    @Test
    void responseIncludesTurnNumberAndUserMessage() {
        String response = assistant.respond("hello", ConversationState.initial().nextTurn());

        assertThat(response).contains("Turn 1");
        assertThat(response).contains("hello");
        assertThat(response).contains("mock streaming response");
    }

    @Test
    void responseIsDeterministicForSameInputAndState() {
        ConversationState state = new ConversationState(3, false);

        String first = assistant.respond("same", state);
        String second = assistant.respond("same", state);

        assertThat(first).isEqualTo(second);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=MockAssistantTest test
```

Expected: compilation failure because `MockAssistant` does not exist.

- [ ] **Step 3: Implement mock assistant**

Create `MockAssistant.java` with exactly:

```java
package top.ilovemyhome.zora.poc.cui.claude;

/** Deterministic assistant used to validate the CUI without calling a real LLM API. */
final class MockAssistant {

    String respond(String userMessage, ConversationState state) {
        String safeMessage = userMessage == null ? "" : userMessage;
        return "Turn " + state.turnCount() + ": I received: " + safeMessage + System.lineSeparator()
            + "This is a mock streaming response from zora-claude-cui-poc.";
    }
}
```

- [ ] **Step 4: Run mock assistant tests to verify they pass**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=MockAssistantTest test
```

Expected: `Tests run: 2, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

---

### Task 8: Implement streaming printer with tests first

**Files:**
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/test/java/top/ilovemyhome/zora/poc/cui/claude/StreamingPrinterTest.java`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/StreamingPrinter.java`

- [ ] **Step 1: Write failing streaming printer tests**

Create `StreamingPrinterTest.java` with exactly:

```java
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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=StreamingPrinterTest test
```

Expected: compilation failure because `StreamingPrinter` does not exist.

- [ ] **Step 3: Implement streaming printer**

Create `StreamingPrinter.java` with exactly:

```java
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
```

- [ ] **Step 4: Run streaming printer tests to verify they pass**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc -Dtest=StreamingPrinterTest test
```

Expected: `Tests run: 2, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

---

### Task 9: Implement shell and application wiring

**Files:**
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiShell.java`
- Create: `zora-poc/zora-cui-poc/zora-claude-cui-poc/src/main/java/top/ilovemyhome/zora/poc/cui/claude/ClaudeCuiApplication.java`

- [ ] **Step 1: Implement shell loop**

Create `ClaudeCuiShell.java` with exactly:

```java
package top.ilovemyhome.zora.poc.cui.claude;

import java.io.PrintWriter;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

/** Main interactive loop for the Claude-like terminal shell. */
final class ClaudeCuiShell {

    private static final String PROMPT = "you > ";

    private final LineReader lineReader;
    private final Terminal terminal;
    private final ClaudeCuiCommandParser commandParser;
    private final MockAssistant assistant;
    private final StreamingPrinter streamingPrinter;

    ClaudeCuiShell(
        LineReader lineReader,
        Terminal terminal,
        ClaudeCuiCommandParser commandParser,
        MockAssistant assistant,
        StreamingPrinter streamingPrinter) {
        this.lineReader = lineReader;
        this.terminal = terminal;
        this.commandParser = commandParser;
        this.assistant = assistant;
        this.streamingPrinter = streamingPrinter;
    }

    void run() {
        ConversationState state = ConversationState.initial();
        printWelcome();
        while (!state.shouldExit()) {
            try {
                String input = lineReader.readLine(PROMPT);
                state = handleInput(input, state);
            } catch (UserInterruptException | EndOfFileException exception) {
                state = state.exit();
            }
        }
        writer().println("bye");
        writer().flush();
    }

    private ConversationState handleInput(String input, ConversationState state) {
        ClaudeCuiCommand command = commandParser.parse(input);
        return switch (command.type()) {
            case EMPTY -> state;
            case HELP -> {
                printHelp();
                yield state;
            }
            case CLEAR -> {
                clearScreen();
                printWelcome();
                yield state;
            }
            case EXIT -> state.exit();
            case UNKNOWN -> {
                writer().println("Unknown command: " + command.content() + ". Type :help for commands.");
                writer().flush();
                yield state;
            }
            case CHAT -> respondToChat(command.content(), state);
        };
    }

    private ConversationState respondToChat(String message, ConversationState state) {
        ConversationState nextState = state.nextTurn();
        streamingPrinter.printAssistantMessage(assistant.respond(message, nextState));
        return nextState;
    }

    private void printWelcome() {
        writer().println("╭────────────────────────────────────────╮");
        writer().println("│ Zora Claude CUI POC                    │");
        writer().println("│ Type :help for commands, :exit to quit │");
        writer().println("╰────────────────────────────────────────╯");
        writer().flush();
    }

    private void printHelp() {
        writer().println("Commands:");
        writer().println("  :help   Show this help message");
        writer().println("  :clear  Clear the terminal and show the welcome banner");
        writer().println("  :exit   Exit the shell");
        writer().println("  :quit   Exit the shell");
        writer().flush();
    }

    private void clearScreen() {
        writer().print("\033[H\033[2J");
        writer().flush();
    }

    private PrintWriter writer() {
        return terminal.writer();
    }
}
```

- [ ] **Step 2: Implement application entry point**

Create `ClaudeCuiApplication.java` with exactly:

```java
package top.ilovemyhome.zora.poc.cui.claude;

import java.io.PrintWriter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/** Application entry point for the JLine-based Claude-like CUI POC. */
public final class ClaudeCuiApplication {

    private static final long STREAM_DELAY_MILLIS = 8L;

    private ClaudeCuiApplication() {
    }

    public static void main(String[] args) {
        try {
            runShell();
        } catch (Exception exception) {
            System.err.println("Failed to start zora-claude-cui-poc: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void runShell() throws Exception {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("zora-claude-cui-poc")
                .build();
            PrintWriter writer = terminal.writer();
            ClaudeCuiShell shell = new ClaudeCuiShell(
                lineReader,
                terminal,
                new ClaudeCuiCommandParser(),
                new MockAssistant(),
                new StreamingPrinter(writer, STREAM_DELAY_MILLIS));
            shell.run();
        }
    }
}
```

- [ ] **Step 3: Compile the module**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc test
```

Expected: all unit tests pass and `BUILD SUCCESS`.

---

### Task 10: Verify build, metadata filtering, and manual CUI startup

**Files:**
- No code changes expected.

- [ ] **Step 1: Run the leaf module tests**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc test
```

Expected: all tests pass and `BUILD SUCCESS`.

- [ ] **Step 2: Run the CUI manually**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc exec:java
```

Expected: terminal shows the welcome banner and `you > ` prompt. Type these inputs manually:

```text
:help
hello
:exit
```

Expected visible behavior:

```text
Commands:
  :help   Show this help message
  :clear  Clear the terminal and show the welcome banner
  :exit   Exit the shell
  :quit   Exit the shell
claude > Turn 1: I received: hello
         This is a mock streaming response from zora-claude-cui-poc.
bye
```

- [ ] **Step 3: Verify the new aggregator from zora-poc**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Verify metadata filtering output exists**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc/zora-claude-cui-poc process-resources
```

Expected: `BUILD SUCCESS` and `target/classes/metadata.json` exists under the leaf module with Maven-filtered values.

---

### Task 11: Run review and final checks

**Files:**
- Review all files changed by this plan.

- [ ] **Step 1: Inspect git diff**

Run:

```bash
git diff -- zora-dependencies/pom.xml zora-poc docs/FEATURE-13-zora-claude-cui-poc.md
```

Expected: diff only contains the JLine dependency management, new CUI POC modules, CUI Java classes/tests, README/metadata/resource files, and the already-approved feature document.

- [ ] **Step 2: Run the full relevant test target**

Run:

```bash
mvn -pl zora-poc/zora-cui-poc test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Request Java code review**

Use the Java reviewer or code-review workflow on the changed Java and Maven files. The review should check:

- JLine dependency is isolated to the POC.
- No real Claude / Anthropic API calls were introduced.
- No API keys or secrets are read or hardcoded.
- Command parsing and conversation state use immutable patterns.
- Tests cover parser, state, mock assistant, and streaming printer behavior.

- [ ] **Step 4: Fix review findings if needed**

If the review reports CRITICAL or HIGH issues, fix them before reporting completion. For each fix, rerun:

```bash
mvn -pl zora-poc/zora-cui-poc test
```

Expected: `BUILD SUCCESS` after fixes.

---

## Self-Review

### Spec coverage

- Module structure from `docs/FEATURE-13-zora-claude-cui-poc.md` is covered by Tasks 2-4.
- JLine-based shell is covered by Tasks 4 and 9.
- `:help`, `:clear`, `:exit`, `:quit`, empty input, unknown commands, and chat input are covered by Task 5 and Task 9.
- Mock assistant and simulated streaming output are covered by Tasks 7-8.
- Maven build/test/run verification is covered by Tasks 10-11.
- README, metadata, and simplelogger resources are covered by Tasks 3-4.
- No real Claude / Anthropic API integration is introduced; this is explicitly verified in Task 11.

### Placeholder scan

The plan contains no `TBD`, `TODO`, `implement later`, or unspecified code steps. Every code-creation step includes exact file contents.

### Type consistency

The plan consistently uses these types and method names:

- `ClaudeCuiCommandType`
- `ClaudeCuiCommand`
- `ClaudeCuiCommandParser.parse(String)`
- `ConversationState.initial()`, `nextTurn()`, `exit()`
- `MockAssistant.respond(String, ConversationState)`
- `StreamingPrinter.printAssistantMessage(String)`
- `ClaudeCuiShell.run()`
- `ClaudeCuiApplication.main(String[])`
