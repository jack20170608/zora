# zora-poc

zora-poc 是 zora 项目的 POC（Proof of Concept）聚合模块，用于探索新框架、验证新功能以及进行各种技术实验。

## 设计原则

- **所有代码放在 test scope**：POC 代码写在 `src/test/java` 下，不会被打包到生产产物中
- **独立子模块**：每个 POC 主题创建独立的子模块，避免依赖混乱
- **快速迭代**：无需严格的生产代码标准，允许快速试错

## 如何添加新的 POC 子模块

1. 在 `zora-poc/` 下创建新的子模块目录，例如 `zora-poc-jooq/`
2. 子模块的 `pom.xml` 继承 `zora-parent`，所有依赖声明为 `test` scope
3. 在 `zora-poc/pom.xml` 的 `<modules>` 中添加该子模块
4. 代码写在子模块的 `src/test/java/` 目录下

## 示例子模块结构

```
zora-poc-jooq/
├── pom.xml
└── src
    └── test
        ├── java
        │   └── top/ilovemyhome/zora/poc/jooq/
        │       └── JooqExploreTest.java
        └── resources
            └── simplelogger.properties
```

## 依赖说明

由于所有代码都在 test scope，子模块的 `pom.xml` 中所有依赖（包括被探索的框架）都应声明为：

```xml
<scope>test</scope>
```

测试基础依赖（junit-jupiter、assertj、mockito、slf4j-simple 等）已统一在 `zora-parent` 中管理，子模块直接引用即可。

## License

Copyright © 2025 zora
