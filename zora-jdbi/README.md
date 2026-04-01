# zora-jdbi

zora-jdbi 是 zora 项目对 JDBI 3 的扩展工具模块，提供便捷的 JDBI 3 集成和常用工具类。

## 模块结构

```
top.ilovemyhome.zora.jdbi/
```

## 依赖

```xml
<dependency>
    <groupId>top.ilovemyhome.zora</groupId>
    <artifactId>zora-jdbi</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

依赖：
- `zora-common` - zora 通用工具
- `jdbi3-core` - JDBI 3核心
- `jdbi3-sqlobject` - JDBI 3 SQL Object API
- `slf4j-api` - 日志门面

## 版本管理

版本通过 `jdbi3-bom` 从 `zora-dependencies` 统一管理，当前版本：**3.51.0**

## License

Copyright © 2025 zora
