# zora-json

JSON processing utilities for zora project, based on Jackson.

## Features

- Provides `JacksonUtil` - static utility class for common JSON operations
- Pre-configured `ObjectMapper` with:
  - Jackson 3 内置的 Java Time 编解码器，通过 `SimpleModule` 覆盖日期格式
  - Custom date/time format patterns:
    - `LocalDate`: `yyyy-MM-dd`
    - `LocalDateTime`: `yyyy-MM-dd HH:mm:ss.SSS`
    - `LocalTime`: `HH:mm:ss.SSS`
    - `YearMonth`: `yyyyMM`
- 默认启用 `FAIL_ON_UNKNOWN_PROPERTIES`，拒绝未知字段
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
import tools.jackson.core.type.TypeReference;

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
- `tools.jackson.core:jackson-databind`（Jackson 3，调用方需要提供）
- `com.fasterxml.jackson.core:jackson-annotations:2.22`（Jackson 3 自身的传递依赖，由 BOM 管理）
- `org.slf4j:slf4j-api` - logging API

## License

This project is part of zora framework.
