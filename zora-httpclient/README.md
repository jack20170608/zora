# zora-httpclient

zora-httpclient 是 zora 项目的 HTTP 客户端工具模块，提供 HTTP 通信的工具封装和简化接口。

## 特性

- 基于 Java HttpClient 的简化封装
- 方便的 REST 客户端，支持 JSON 处理
- 自动重试机制
- URL 构建工具
- 支持 multipart 表单上传
- 可自定义 SSL 上下文（支持信任所有证书用于测试）
- 支持异常处理的函数式接口
- 统一异常处理

## 模块结构

```
top.ilovemyhome.zora.httpclient/
├── domain/                 - 数据模型
│   └── HTTPRequestMultipartBody    - Multipart 请求体封装
├── exception/              - 异常处理函数式接口
│   ├── ThrowingConsumer           - 支持抛出检查异常的 Consumer
│   ├── ThrowingFunction           - 支持抛出检查异常的 Function
│   └── ThrowingRunnable           - 支持抛出检查异常的 Runnable
├── HttpClients             - HTTP 客户端构建工厂
├── HttpRetryClient         - 带重试机制的 HTTP 客户端包装
├── RestClient              - 简化的 REST 客户端
├── TrustAllSslContext      - 信任所有证书的 SSL 上下文工具（用于测试）
└── URLBuilder              - URL 构建工具
```

## 依赖

```xml
<dependency>
    <groupId>top.ilovemyhome.zora</groupId>
    <artifactId>zora-httpclient</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

依赖：
- `zora-common` - zora 通用工具
- `zora-text` - 文本处理工具
- `org.slf4j:slf4j-api` - 日志门面
- `java.net.http` - Java 内置 HttpClient（Java 11+）

所有第三方依赖版本通过 `zora-dependencies` 统一管理。测试依赖提供：
- `mu-server` - 嵌入式 Web 服务器用于集成测试
- `jackson-databind` - JSON 处理
- `mockito-core` - 单元测试 Mock 框架

## License

Copyright © 2025-2026 zora
