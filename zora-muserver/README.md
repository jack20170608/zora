# zora-muserver

zora-muserver 是 zora 项目的 [Muserver](https://muserver.io/) 工具扩展模块，提供嵌入式 Web 服务器的工具封装和安全认证扩展。

Muserver 是一个轻量级、高性能的嵌入式 Java Web 服务器。

## 特性

- 内置请求日志处理器
- 客户端真实 IP 获取处理器
- 完整的安全认证框架
  - 内存用户名密码认证
  - LDAP 用户名密码认证
  - JWT Token 认证
  - Session 认证
  - Cookie 认证
  - IP/CIDR 地址校验
- Bearer Token 和 Cookie 认证过滤器
- 简单基于角色的访问控制

## 模块结构

```
top.ilovemyhome.zora.muserver/
├── handler/                - 常用处理器实现
│   ├── ClientNetAddressHandler      - 客户端真实IP获取
│   └── HttpRequestLoggerHandler      - 请求日志记录
├── helper/                 - 帮助工具类
│   └── MuRequestHelper              - 请求处理帮助
└── security/               - 安全认证框架
    ├── AppSecurityContext           - 安全上下文
    ├── authenticator/               - 认证器实现
    │   ├── InMemoryUserPassAuthenticator
    │   ├── JwtAuthenticator
    │   ├── LdapUserPassAuthenticator
    │   ├── SessionAuthenticator
    │   └── TokenAuthenticator
    ├── authorizer/                 - 访问控制
    │   └── SimpleRoleAuthorizer
    ├── core/                     - 核心基础类
    │   ├── CIDRValidator
    │   ├── IpValidator
    │   ├── PatternValidator
    │   └── User
    └── filter/                   - 安全过滤器
        ├── BearerAuthSecurityFilter
        ├── CookieAuthSecurityFilter
        └── ContainerRequestFilterFacet
```

## 依赖

```xml
<dependency>
    <groupId>top.ilovemyhome.zora</groupId>
    <artifactId>zora-muserver</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

依赖：
- `zora-common` - zora 通用工具
- `zora-config` - 配置加载
- `io.muserver:mu-server` - Muserver 核心库
- `org.slf4j:slf4j-api` - 日志门面

所有第三方依赖版本通过 `zora-dependencies` 统一管理。

## License

Copyright © 2025-2026 zora
