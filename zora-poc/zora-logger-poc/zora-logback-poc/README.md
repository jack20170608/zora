# zora-logback-poc

Logback logging framework exploration sub-module.

## Purpose

This module is dedicated to exploring and experimenting with Logback features and configurations.
All code lives in `test` scope -- nothing is packaged into production artifacts.

## Scope

- Logback configuration patterns
- Appenders (console, file, rolling, async, sifting)
- Layouts and encoders
- Filters and level configurations
- Integration with SLF4J
- Performance characteristics
- Programmatic logger/appender creation
- MDC-based dynamic file routing

## Examples Overview

### 1. Multiple File Appenders (`MultipleFileLoggingTest`)

Demonstrates routing logs to different files using **static XML configuration**.

**Patterns covered:**
- Dedicated logger name → dedicated file (`audit.log`)
- Service-layer logger → multiple files (`application.log` + `rolling.log`)
- Level-based filtering → `error.log` only receives `ERROR` messages
- Daily rolling file appender

**Configuration:** `src/test/resources/logback-test.xml`

### 2. Dynamic Logger Creation (`DynamicLoggerCreationTest`)

Demonstrates **pure Java code** to create loggers and appenders at runtime.

**Patterns covered:**
- Create a simple `FileAppender` and attach it to a new logger
- Multi-tenancy: each tenant gets its own log file dynamically
- Dynamic `RollingFileAppender` with `TimeBasedRollingPolicy`
- Attach a new appender to an existing logger at runtime

**Key APIs:**
```java
LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
Logger logger = context.getLogger("DYNAMIC.MyLogger");
logger.addAppender(appender);
```

### 3. SiftingAppender (`SiftingAppenderTest`)

Demonstrates **declarative dynamic file routing** based on an MDC key.

**Concept:** `SiftingAppender` inspects an MDC value (e.g. `tenantId`) and automatically
routes the log event to the correct file appender. This is the preferred approach
when the routing key is known at logging time and you want to avoid boilerplate code.

**Configuration snippet:**
```xml
<appender name="SIFTING" class="ch.qos.logback.classic.sift.SiftingAppender">
    <discriminator class="ch.qos.logback.classic.sift.MDCBasedDiscriminator">
        <key>tenantId</key>
        <defaultValue>unknown</defaultValue>
    </discriminator>
    <sift>
        <appender name="TENANT-${tenantId}" class="ch.qos.logback.core.FileAppender">
            <file>target/logs/tenant-${tenantId}.log</file>
            ...
        </appender>
    </sift>
</appender>
```

**Usage:**
```java
MDC.put("tenantId", "acme");
logger.info("Order created");
MDC.clear();
```

### 4. Comparison: Dynamic Creation vs SiftingAppender

| Approach | When to use | Pros | Cons |
|---|---|---|---|
| **XML Static Config** | Fixed, known log routing | Simple, declarative, easy to maintain | Not flexible at runtime |
| **SiftingAppender** | Routing key is in MDC, many possible values | Zero Java code, auto-managed | Limited to one discriminating key per appender |
| **Programmatic** | Complex runtime logic, non-MDC keys | Full control, any logic | More code, manual lifecycle management |

## Running Tests

Run a single test class:

```bash
mvn test -pl zora-poc/zora-logger-poc/zora-logback-poc -Dtest=MultipleFileLoggingTest
mvn test -pl zora-poc/zora-logger-poc/zora-logback-poc -Dtest=DynamicLoggerCreationTest
mvn test -pl zora-poc/zora-logger-poc/zora-logback-poc -Dtest=SiftingAppenderTest
```

Run all tests in the module:

```bash
mvn test -pl zora-poc/zora-logger-poc/zora-logback-poc
```

Or from the module directory:

```bash
cd zora-poc/zora-logger-poc/zora-logback-poc
mvn test
```

## Log Output Locations

During tests, log files are written to:

| Directory | Used by |
|---|---|
| `target/logs/` | Static XML configuration (`MultipleFileLoggingTest`, `SiftingAppenderTest`) |
| `target/dynamic-logs/` | Programmatic configuration (`DynamicLoggerCreationTest`) |
