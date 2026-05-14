# zora-unittest-poc 设计方案

## 背景
在 zora-poc 项目下新建一个聚合模块 zora-unittest-poc，用于对主流 Java 单元测试框架进行功能和特性预研。

## 模块层级结构

```
zora-poc (已存在，需更新 modules 列表)
└── zora-unittest-poc (新增，pom aggregator)
    └── zora-junit5-poc (新增，jar 叶子模块)
```

## 模块职责

- **zora-unittest-poc**：pom 打包的聚合模块，本身不包含业务代码，负责聚合子模块并统一说明单元测试 POC 的目标。
- **zora-junit5-poc**：唯一叶子模块，jar 打包。所有预研代码（JUnit 5、AssertJ、Mockito）均通过测试用例形式存放在 `src/test/java` 中。

## 技术选型（均在 test scope）

| 框架 | 用途 |
|---|---|
| JUnit Jupiter (API + Engine) | 核心测试运行框架，支持生命周期、参数化、动态测试等 |
| AssertJ | 流式断言，提升测试可读性 |
| Mockito (core + junit-jupiter) | Mock/Spy/Stub，模拟依赖行为 |
| SLF4J Simple | 测试日志输出，配合 simplelogger.properties 配置 |

## 预研内容规划

### JUnit 5
- 基本测试注解（@Test, @BeforeEach, @AfterEach, @BeforeAll, @AfterAll）
- 参数化测试（@ParameterizedTest + @ValueSource, @CsvSource）
- 动态测试（@TestFactory）
- 嵌套测试（@Nested）
- 条件执行（@EnabledOnOs, @DisabledIf 等）
- 扩展机制（自定义 Extension）

### AssertJ
- 基本流式断言（assertThat().isEqualTo()）
- 异常断言（assertThatThrownBy）
- 集合断言（assertThat(list).containsExactly()）
- 自定义断言

### Mockito
- 基本 Mock 与 Stub（when/thenReturn）
- 验证调用（verify）
- 参数匹配器（any(), eq(), argThat()）
- Spy（部分 Mock）
- @Mock / @InjectMocks 注解用法

## 文件约定

每个模块均按 CLAUDE.md 要求包含：
- `README.md`（中文，说明功能和使用方法）
- `metadata/metadata.json`（模块元数据，启用 filtering）
- `src/main/resources/` + `src/test/resources/`
- `src/test/resources/simplelogger.properties`（SLF4J Simple 日志配置）

## 版本管理

zora-junit5-poc 的 version 从根目录 `VERSION` 文件中读取（通过 `${revision}` 继承）。
