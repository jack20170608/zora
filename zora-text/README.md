# zora-text

zora-text 是 zora 项目的文本处理工具模块，提供各种文本解析、处理、转换、匹配等能力。

## 特性

- 文本分割与拼接
- 字符串格式化与模板
- 正则表达式工具封装
- 相似度计算
- 编码检测与转换
- 占位符替换

## 模块结构

```
top.ilovemyhome.zora/
├── split/          - 文本分割工具
├── template/       - 模板与占位符处理
├── regex/          - 正则表达式工具
├── similarity/     - 文本相似度计算
├── encoding/       - 编码检测与转换
└── sanitize/       - 文本清理与净化
```

## 依赖

```xml
<dependency>
    <groupId>top.ilovemyhome.zora</groupId>
    <artifactId>zora-text</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

依赖：
- `zora-common` - zora 通用工具
- `org.slf4j:slf4j-api` - 日志门面

所有第三方依赖版本通过 `zora-dependencies` 统一管理。

## License

Copyright © 2025-2026 zora
