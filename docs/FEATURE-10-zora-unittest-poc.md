# FEATURE-10: zora-unittest-poc 单元测试框架预研模块

## 概述

`zora-unittest-poc` 是 `zora-poc` 项目下的一个聚合模块，用于对主流 Java 单元测试框架进行功能和特性预研。该模块采用与 `zora-java-logger-poc` 相同的三层聚合结构，所有代码均处于 `test` scope，不会被打包到生产产物中。

## 模块结构

```
zora-poc (已存在，更新 modules)
└── zora-unittest-poc (新增，pom aggregator)
    └── zora-junit5-poc (新增，jar 叶子模块)
```

## 各模块职责

### zora-unittest-poc

- 类型：pom aggregator
- 职责：聚合单元测试 POC 子模块，无实际业务代码
- 子模块：`zora-junit5-poc`

### zora-junit5-poc

- 类型：jar
- 职责：包含所有预研测试代码，覆盖 JUnit 5、AssertJ、Mockito 三个框架
- 预研内容：
  - **JUnit 5**：生命周期、基本断言、参数化测试、动态测试、嵌套测试、重复测试
  - **AssertJ**：流式断言、异常断言、集合断言、对象字段断言
  - **Mockito**：Mock/Stub、参数匹配器、验证调用、Spy、@Mock/@InjectMocks 注解

## 依赖管理

所有测试依赖的版本均由 `zora-dependencies` 统一管理：

| 依赖 | 版本 | scope |
|---|---|---|
| junit-jupiter-api/engine/params | 5.14.4 | test |
| assertj-core | 3.27.7 | test |
| mockito-core / mockito-junit-jupiter | 5.23.0 | test |
| slf4j-simple | 2.0.17 | test |

## 测试运行方式

从模块目录：

```bash
mvn test
```

从项目根目录：

```bash
mvn test -pl zora-poc/zora-unittest-poc/zora-junit5-poc
```

## 文件约定

每个模块均包含：
- `README.md`（中文说明）
- `metadata/metadata.json`（模块元数据，启用 filtering）
- `src/main/resources/` + `src/test/resources/`
- `src/test/resources/simplelogger.properties`（SLF4J Simple 日志配置）

## 测试统计

当前共 27 个测试用例：
- `JUnit5BasicTest`：6 个（含 1 个 disabled）
- `JUnit5AdvancedTest`：12 个（参数化 + 动态测试）
- `AssertJExploreTest`：4 个
- `MockitoExploreTest`：5 个
