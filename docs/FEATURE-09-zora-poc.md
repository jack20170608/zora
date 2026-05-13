# FEATURE-09: zora-poc POC 聚合模块

## 背景

在 zora 项目开发过程中，经常需要快速验证某个第三方框架、Java 新特性或者某种设计思路是否可行。如果直接在现有的功能模块中进行实验，会引入不必要的依赖污染和生产代码风险。

## 目标

创建一个专门的 POC（Proof of Concept）聚合模块 `zora-poc`，用于：
- 探索新的第三方框架
- 验证 Java 新特性的使用方式
- 进行技术预研和快速原型开发
- 所有实验代码隔离在 test scope，不影响生产产物

## 设计决策

### 模块类型：pom 聚合模块

`zora-poc` 本身是一个 `pom` 类型的聚合模块，不直接包含代码。具体的 POC 实验以子模块的形式存在，例如：
- `zora-poc-jooq` —— 探索 JOOQ 框架
- `zora-poc-vertx` —— 探索 Vert.x 响应式编程
- `zora-poc-native-image` —— 探索 GraalVM Native Image

### 代码全部放在 test scope

这是 `zora-poc` 的核心设计原则：
- 所有 Java 代码写在 `src/test/java/` 下
- 所有依赖（包括被实验的框架）声明为 `test` scope
- 不会生成任何生产 jar，也不会污染主 artifact

### 继承 zora-parent

`zora-poc` 作为聚合模块，继承 `zora-parent` 以复用统一的依赖管理和插件配置。

## 模块结构

```
zora-poc/
├── pom.xml              # 聚合模块 POM
├── metadata/
│   └── metadata.json    # 模块元数据
└── README.md            # 使用说明
```

## 根 POM 变更

在根 `pom.xml` 的 `<modules>` 中添加：

```xml
<module>zora-poc</module>
```

## 后续使用

当需要进行某项技术实验时：

1. 在 `zora-poc/` 下创建新的子模块目录
2. 编写子模块的 `pom.xml`，所有依赖使用 `test` scope
3. 在 `zora-poc/pom.xml` 中注册子模块
4. 在子模块的 `src/test/java` 下编写实验代码
