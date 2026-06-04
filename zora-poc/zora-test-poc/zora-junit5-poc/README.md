# zora-junit5-poc

JUnit 5、AssertJ、Mockito 功能与特性预研子模块。

## 目的

本模块通过测试用例形式，对以下三个主流 Java 单元测试框架进行功能和特性预研，所有代码均在 `test` scope 下运行。

## 预研范围

### JUnit 5
- 基本测试注解（@Test, @BeforeEach, @AfterEach, @BeforeAll, @AfterAll）
- 参数化测试（@ParameterizedTest）
- 动态测试（@TestFactory）
- 嵌套测试（@Nested）

### AssertJ
- 流式基本断言
- 异常断言
- 集合断言

### Mockito
- Mock 与 Stub
- 验证调用
- Spy 与注解用法

## 运行测试

```bash
mvn test -pl zora-junit5-poc
```

或从项目根目录：

```bash
mvn test -pl zora-poc/zora-unittest-poc/zora-junit5-poc
```
