# zora-json

JSON processing utilities for zora project, based on Jackson.

## Features

- Provides `JacksonUtil` - static utility class for common JSON operations
- Pre-configured `ObjectMapper` with:
  - `JavaTimeModule` for Java 8+ `java.time` API support
  - Custom date/time format patterns:
    - `LocalDate`: `yyyy-MM-dd`
    - `LocalDateTime`: `yyyy-MM-dd HH:mm:ss.SSS`
    - `LocalTime`: `HH:mm:ss.SSS`
    - `YearMonth`: `yyyyMM`
- Disabled `FAIL_ON_UNKNOWN_PROPERTIES` by default
- Pretty-print output enabled by default

## Maven Dependency

```xml
<dependency>
    <groupId>top.ilovemyhome.zora</groupId>
    <artifactId>zora-json</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Usage

```java
import top.ilovemyhome.zora.json.jackson.JacksonUtil;

// Serialize object to JSON string
String json = JacksonUtil.toJson(obj);

// Deserialize JSON to object
MyObj obj = JacksonUtil.fromJson(json, MyObj.class);

// Deserialize JSON to generic type
List<MyObj> list = JacksonUtil.fromJson(json, new TypeReference<List<MyObj>>() {});
```

## Date/Time Format Configuration

Default date/time formats are defined in `top.ilovemyhome.zora.json.Constants`:

| Type | Format |
|------|--------|
| `LocalDate` | `yyyy-MM-dd` |
| `LocalDateTime` | `yyyy-MM-dd HH:mm:ss.SSS` |
| `LocalTime` | `HH:mm:ss.SSS` |
| `YearMonth` | `yyyyMM` |

## Dependencies

- `zora-common` - zora common utilities
- `com.fasterxml.jackson.core:jackson-databind` - Jackson core
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` - Java 8 date/time support
- `org.slf4j:slf4j-api` - logging API

## License

This project is part of zora framework.
