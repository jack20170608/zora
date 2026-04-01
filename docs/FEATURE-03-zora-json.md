# FEATURE-03: zora-json JSON 处理工具模块

## 概述

zora-json 是 zora 项目的 JSON 处理工具模块，基于 Jackson 提供统一的 JSON 处理能力，封装常用的 JSON 操作，提供更简洁的 API 接口。

## 模块定位

为什么需要独立的 JSON 模块：

1. **统一依赖管理**：整个项目统一使用 Jackson 进行 JSON 处理，避免多个 JSON 库混用
2. **统一抽象**：提供常用 JSON 操作的工具方法，简化代码
3. **易于扩展**：可以方便地添加项目特定的 JSON 扩展功能
4. **版本统一**：所有模块依赖同一版本的 Jackson，减少依赖冲突风险

## 依赖说明

### 主要依赖

| 依赖 | 说明 | 版本管理 |
|------|------|----------|
| `zora-common` | zora 公共工具类 | 项目内部依赖 |
| `com.fasterxml.jackson.core:jackson-databind` | Jackson JSON 库 | 通过 `jackson-bom` 管理 |
| `org.slf4j:slf4j-api` | 日志接口 | 由父项目统一管理 |

### 测试依赖

- `junit-jupiter-api` - JUnit 5 测试框架
- `assertj-core` - 断言库
- `mockito-core` - Mock 测试框架
- `slf4j-simple` - 测试用 SLF4J 简单实现

## 目录结构

```
zora-json/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/top/ilovemyhome/zora/json/
    │   │   └── 【JSON 工具类和扩展类放在这里】
    │   └── resources/        # 资源文件目录（已创建）
    └── test/
        ├── java/top/ilovemyhome/zora/json/
        │   └── 【单元测试放在这里】
        └── resources/        # 测试资源文件目录（已创建）
```

## 预期功能

zora-json 模块可以提供以下功能：

- **JsonUtils** - 静态工具类，简化常见 JSON 操作：
  - `jsonToObject(String json, Class<T> clazz)` - JSON 转对象
  - `jsonToObject(String json, TypeReference<T> typeRef)` - JSON 转泛型对象
  - `objectToJson(Object obj)` - 对象转 JSON 字符串
  - `objectToPrettyJson(Object obj)` - 对象转格式化 JSON
  - `jsonToMap(String json)` - JSON 转 Map
  - `mapToJson(Map map)` - Map 转 JSON

- **Jackson 配置定制** - 提供项目统一的 ObjectMapper 配置

- **常用模块注册** - 默认注册 Java 8 时间模块等常用模块

## 使用方式

在需要 JSON 处理的模块中添加依赖：

```xml
<dependency>
    <groupId>top.ilovemyhome.zora</groupId>
    <artifactId>zora-json</artifactId>
</dependency>
```

## 设计原则

1. **轻量级**：不做过度封装，只提供项目需要的工具方法
2. **不侵入**：不强制使用，可以直接使用原生 Jackson API
3. **可定制**：允许用户自定义 ObjectMapper
4. **向后兼容**：遵循 Jackson 的版本升级节奏

## 注意事项

1. 本模块只是工具封装，并不重新发明 JSON 库，Jackson 的强大功能仍可直接使用
2. 推荐使用 `zora-json` 提供的 `JsonUtils` 进行简单 JSON 操作，复杂场景直接使用 `ObjectMapper`
3. 保持 Jackson 版本和 `jackson-bom` 中定义的一致，不要随意修改版本
