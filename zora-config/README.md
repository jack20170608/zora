# zora-config

zora-config 是 zora 项目的配置加载与管理模块，基于 [Typesafe Config](https://github.com/lightbend/config) 封装，提供统一便捷的配置加载、解析、POJO 绑定能力。

## 特性

- 支持按环境加载配置（默认配置 + 环境特定配置覆盖）
- 支持配置自动转换为 POJO Java Bean
- 支持配置列表转换为 Bean 列表
- 支持自定义 ClassLoader
- 支持系统属性/环境变量覆盖
- 遵循 Typesafe Config 的 [HOCON](https://github.com/lightbend/config/blob/master/README.md#human-optimized-config-object-notation) 格式

## 模块结构

```
top.ilovemyhome.zora.config/
└── ConfigLoader.java          - 配置加载工具入口点
```

## 主要 API

```java
// 按环境加载配置（默认 application.conf + application-{env}.conf）
Config config = ConfigLoader.loadConfigByEnv("dev");

// 加载单个配置文件
Config config = ConfigLoader.loadConfig("config/application.conf");

// 带 fallback 加载（特定配置覆盖默认配置）
Config config = ConfigLoader.loadConfig("application.conf", "application-dev.conf");

// 加载为指定路径下的 Bean
DatabaseConfig dbConfig = ConfigLoader.loadConfigAsBean(
    "config/application-dev.conf", "database", DatabaseConfig.class);

// 加载配置对象列表
List<DatabaseConfig> databases = ConfigLoader.loadConfigAsBeanList(
    config, "databases", DatabaseConfig.class);
```

## 依赖

```xml
<dependency>
    <groupId>top.ilovemyhome.zora</groupId>
    <artifactId>zora-config</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

依赖：
- `zora-common` - zora 通用工具
- `org.slf4j:slf4j-api` - 日志门面
- `com.typesafe:config` - Typesafe Config 核心库
- `com.fasterxml.jackson.core:jackson-databind` - JSON 解析（可选，用于 JSON/YAML 支持）

所有第三方依赖版本通过 `zora-dependencies` 统一管理。

## 使用示例

### 1. 配置文件

```hocon
# application.conf (default)
database {
  url = "NOT-SET"
  user = "DUMMY"
  password = "foo"
}
app {
  context-path = "are you ok"
}
```

```hocon
# application-dev.conf (dev specific)
database {
  url = "jdbc:localhost:1234:foo"
  user = "app_user"
  password = "1"
}
```

### 2. 定义 Bean

```java
public class DatabaseConfig {
    private String url;
    private String user;
    private String password;

    // Getters and setters required
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    // ... other getters/setters
}
```

### 3. 加载配置

```java
// Load by environment
Config config = ConfigLoader.loadConfigByEnv("dev");

// Or load directly as bean
DatabaseConfig dbConfig = ConfigLoader.loadConfigAsBean(
    "config/application-dev.conf", "database", DatabaseConfig.class);
```

## License

Copyright © 2025-2026 zora
