# zora-logback-poc

Logback logging framework exploration sub-module.

## Purpose

This module is dedicated to exploring and experimenting with Logback features and configurations.
All code lives in `test` scope -- nothing is packaged into production artifacts.

## Scope

- Logback configuration patterns
- Appenders (console, file, rolling, async)
- Layouts and encoders
- Filters and level configurations
- Integration with SLF4J
- Performance characteristics

## Running Tests

```bash
mvn test -pl zora-logback-poc
```

Or from the project root:

```bash
mvn test -pl zora-poc/zora-java-logger-poc/zora-logback-poc
```
