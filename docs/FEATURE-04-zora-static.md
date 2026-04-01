# FEATURE-04: zora-static 静态资源聚合模块

## 创建时间
2026-03-28

## 模块描述

`zora-static` 是一个 pom 类型的聚合模块，用于统一管理项目中的静态资源文件。按照 CLAUDE.md 开发指引，在初始化模块时已经创建了标准的 `src/main/resources` 和 `src/test/resources` 目录结构，方便后续存放各类静态资源。

## 模块结构

```
zora-static/
├── README.md
├── pom.xml
└── src/
    ├── main/
    │   └── resources/          # 主静态资源目录，存放生产环境使用的静态资源
    └── test/
        └── resources/          # 测试静态资源目录，存放测试使用的静态资源
```

## 用途

该模块可用于存放以下类型的静态资源：

1. **SQL 迁移脚本** - Flyway 或其他数据库迁移工具使用的 SQL 文件
2. **配置文件模板** - 各种配置文件的模板，供应用程序动态填充
3. **JSON  Schema 文件** - 用于验证 JSON 数据结构的 Schema 文件
4. **前端静态资源** - HTML、CSS、JavaScript 等前端资源
5. **证书和密钥文件** - SSL/TLS 证书（仅用于开发测试，生产环境建议使用外部管理）
6. **其他静态内容** - 任何不需要编译的静态资源

## 使用方式

### 作为依赖引入

如果需要将 zora-static 中的静态资源打包到其他模块，可以在对应模块的 pom.xml 中添加依赖：

```xml
<dependency>
    <groupId>top.ilovemyhome.zora</groupId>
    <artifactId>zora-static</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 添加子模块

当静态资源按类别需要分开管理时，可以在 zora-static 下创建子模块：

```xml
<!-- 在 zora-static/pom.xml 的 modules 中添加子模块 -->
<modules>
    <module>zora-static-sql-migrations</module>
</modules>
```

## 构建说明

由于该模块是 pom 类型，Maven 不会编译生成 jar 包，只会处理资源文件。资源文件会被自动复制到 target/classes 目录，可以被其他模块通过类路径访问。

## 遵循的开发规范

- [x] 遵循项目模块结构约定
- [x] 创建 `src/main/resources` 和 `src/test/resources` 目录
- [x] 添加模块级 README.md 说明文档
- [x] 在 docs 目录创建特性文档
- [x] 根 pom.xml 添加模块引用
- [x] zora-bom 添加依赖管理配置
