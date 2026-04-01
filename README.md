# Zora

Zora 是一个轻量级、模块化的 Java 开发工具框架，基于最新的 Java 25 特性构建，提供一系列常用的工具类和功能模块，帮助开发者更高效地进行 Java 开发。 
Zora 的设计理念是保持代码简洁、易用，同时充分利用现代 Java 生态中的最佳实践和工具。无论你是构建 Web 应用、处理数据还是进行系统集成，Zora 都能为你提供可靠的基础设施和实用的工具。

## 设计理念

1. **轻量级**: 保持代码简洁，避免过度设计和复杂依赖
2. **模块化**: 功能模块化设计，开发者可按需选择使用
3. **现代化**: 充分利用最新 Java 特性，拥抱现代 Java 生态
4. **易用性**: 简洁直观的 API 设计，降低学习成本

## 项目结构

```
zora/
├── docs/                 # 架构和功能文档
├── zora-bom/             # Maven BOM (Bill of Materials)
├── zora-cli-tools/       # CLI 工具集 (包含 zora-flyway-cli)
├── zora-common/          # 核心通用工具
├── zora-config/          # 配置管理
├── zora-dependencies/    # 依赖版本管理
├── zora-httpclient/      # HTTP 客户端工具
├── zora-jdbi/            # JDBI 3 集成
├── zora-json/            # JSON 处理
├── zora-muserver/        # Muserver 扩展
├── zora-parent/          # 父 POM
├── zora-rdb/             # 关系数据库工具
├── zora-static/          # 静态资源管理
├── zora-text/            # 文本处理
└── pom.xml               # 根 POM
```

## 模块说明

| 模块 | 描述 | 主要依赖 |
|------|------|----------|
| **zora-common** | 核心工具类库，包含 IO、集合、日期、编解码、验证、系统工具等 | Apache Commons Lang3, Guava |
| **zora-config** | 基于 Typesafe Config 的类型安全配置管理，支持环境加载和 POJO 绑定 | Typesafe Config |
| **zora-httpclient** | 基于 Java 11+ HttpClient 的 REST 客户端封装，支持重试、多文件上传 | Java HttpClient |
| **zora-json** | Jackson 封装，预配置 Java 8+ 日期时间支持 | Jackson 2.21.x |
| **zora-rdb** | 关系数据库工具，包含 HikariCP 连接池、Flyway 迁移、JDBC 工具 | HikariCP, Flyway |
| **zora-jdbi** | JDBI 3 集成，提供更便捷的数据库访问 | JDBI 3.51.x |
| **zora-muserver** | Muserver 安全扩展，提供 JWT、会话管理、LDAP、内存认证、IP 验证 | Muserver 2.2.x |
| **zora-text** | 文本处理工具，支持分割、模板、正则、编码清洗 | 纯 Java |
| **zora-cli-tools** | 命令行工具，目前包含 Flyway 迁移工具 | picocli |

## 技术栈

- **Java**: 25
- **构建工具**: Maven 3.8.x
- **日志**: SLF4J 2.0.x + Logback 1.5.x
- **测试**: JUnit 5.14.x, Mockito 5.18.x, AssertJ 3.27.x
- **数据库**: PostgreSQL 17.x, H2 2.4.x (测试用)
- **连接池**: HikariCP 7.0.x

## 快速开始

### 使用 BOM 管理依赖

在你的 `pom.xml` 中引入：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>top.ilovemyhome</groupId>
      <artifactId>zora-bom</artifactId>
      <version>${zora.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

然后按需引入模块：

```xml
<dependencies>
  <!-- 通用工具 -->
  <dependency>
    <groupId>top.ilovemyhome</groupId>
    <artifactId>zora-common</artifactId>
  </dependency>

  <!-- JSON 处理 -->
  <dependency>
    <groupId>top.ilovemyhome</groupId>
    <artifactId>zora-json</artifactId>
  </dependency>
</dependencies>
```

### 构建项目

**Unix/Linux/macOS:**
```bash
./build.sh clean install
```

**Windows:**
```bash
build.bat clean install
```

可用的构建命令：
- `clean` - 清理
- `compile` - 编译
- `test` - 运行测试
- `package` - 打包
- `install` - 安装到本地仓库
- `clean install` - 完整构建

## 开发指南

项目遵循以下开发规范：

- 每个子模块都包含 `metadata/metadata.json` 描述模块信息
- 每个子模块都有独立的 README.md 说明使用方法
- 重要架构和功能变更记录在 `docs/` 目录下
- 代码注释使用英文，项目文档使用中文

## 许可证

本项目采用 **Creative Commons Attribution 4.0 International (CC BY 4.0)** 许可证。

你可以自由：
- **共享 — 复制和分发本项目中的任何媒介或格式
- **改编 — 重新混合、转换和基于本项目进行创作，无论商业或非商业用途

唯一要求：
- **署名** — 你必须给出适当的署名，提供指向本许可证的链接，同时标明是否对原作进行了修改。

详细信息请查看 [LICENSE](./LICENSE) 文件。

## 作者

Zora 项目由 [jack github](https://github.com/jack20170608) 维护
