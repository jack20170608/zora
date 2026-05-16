# zora-logger-common

Shared utilities and test infrastructure for Java logging framework exploration.

## Purpose

This module provides reusable components used by multiple POC sub-modules (e.g.
`zora-logback-poc`). All code is production-grade utility code, but the module
itself is kept lightweight with minimal dependencies.

## Provided Components

### 1. MockHttpServer

A lightweight embedded HTTP server for integration-style logging tests.

- Runs on a random free port (or a specified port).
- Supports custom request handlers.
- Auto-starts and auto-stops via try-with-resources or explicit lifecycle.

**Usage:**
```java
try (MockHttpServer server = MockHttpServer.create(0)) {
    server.registerHandler("/api/log", exchange -> {
        // handle request
        exchange.sendResponseHeaders(200, 0);
    });
    server.start();

    // your test code that sends HTTP requests
    URI uri = server.baseUri().resolve("/api/log");
    // ...
}
```

### 2. LogTestUtils

Assertion helpers for log file verification in tests.

- `assertLogFileContains(Path, String...)` – assert a log file contains expected lines.
- `cleanLogDirectory(Path)` – safely delete log files between tests.
- `readLines(Path)` – read all lines with proper encoding.

**Usage:**
```java
LogTestUtils.assertLogFileContains(
    Paths.get("target/logs/app.log"),
    "Order created",
    "Payment processed"
);
```

## Module Layout

```
src/main/java/    – production utilities (MockHttpServer, LogTestUtils)
src/test/java/    – unit tests for the utilities themselves
```

## Dependencies

- `slf4j-api` (compile scope)
- JUnit 5 + AssertJ + Mockito (test scope only)
