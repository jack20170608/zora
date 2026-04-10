# zora-jdbi 模块化架构改进设计

## 概述

zora-jdbi 是 zora 项目中基于 JDBI 3 的数据库访问工具模块。当前模块功能完整，但在模块化职责划分方面存在改进空间：
- 部分大类职责过重（`SqlGenerator` 240行，`BaseDaoJdbiImpl` 306行）
- 设计模式使用可以更现代化（`TableDescription` 本可使用 Java Record）
- 接口设计不够安全（`SearchCriteria` 默认方法返回 null，易触发 NPE）

本文档描述针对这些问题的模块化改进方案。

## 设计目标

1. **保持兼容性**：不破坏现有公有 API，向下兼容
2. **单一职责**：每个类只做一件事，提高可测试性和可维护性
3. **现代化**：利用 Java 新特性简化代码
4. **零新依赖**：不增加项目外部依赖
5. **渐进式**：小步重构，风险可控

## 改进方案

### 1. TableDescription - 改用 Java Record

**现状**：`TableDescription` 是一个不可变数据类，手写了所有 getter 和构造器，共 113 行代码，样板代码较多。

**改进**：
- 将 `TableDescription` 改为 Java Record，自动生成 equals/hashCode/toString/getter
- 保留 Builder 内部类，保持现有构建方式不变
- 减少约 50 行样板代码

**变更范围**：`TableDescription.java` 重构，所有公有 API 保持不变。

### 2. SqlGenerator - 按职责拆分

**现状**：`SqlGenerator` 一个类处理：
- CRUD SQL 生成（create/update/delete/select）
- COUNT SQL 生成
- WHERE 条件拼接
- ORDER BY 排序生成
- LIMIT/OFFSET 分页处理

总计 239 行，职责过重。

**改进**：
```
top.ilovemyhome.zora.jdbi.sql/
├── BaseSqlGenerator       - 基础 CRUD SQL 生成（抽象出公共部分）
├── InsertSqlBuilder       - INSERT 语句构建
├── UpdateSqlBuilder       - UPDATE 语句构建
├── DeleteSqlBuilder       - DELETE 语句构建
├── SelectSqlBuilder       - SELECT 查询构建
├── CountSqlBuilder        - COUNT 查询构建
├── OrderClauseBuilder     - ORDER BY 排序构建（包含 SQL 注入防护）
└── SqlGenerator           - 保持为外观类，兼容原有 API
```

- 每个构建器只负责一类 SQL 生成
- `SqlGenerator` 组合各个构建器，保持原有公有方法签名不变

### 3. SearchCriteria - 从接口改为可扩展基类

**现状**：`SearchCriteria` 是一个接口，所有方法默认返回 `null`：
```java
default Map<String, Object> normalParams() {
    return null;
}
default Map<String, ?> listParam() {
    return null;
}
```

这迫使调用者需要做 null 检查，容易遗漏导致 NPE。

**改进**：
- 保留 `SearchCriteria` 接口
- 新增 `BaseSearchCriteria` 抽象基类，提供：
  - `Map<String, Object> normalParams` 存储和空集合默认实现
  - `Map<String, List<?>> listParams` 存储和默认实现
  - 抽象方法 `whereClause()` 由子类实现
- 新增 `SimpleSearchCriteria` 具体实现类，支持链式调用构建 where 条件

**变更后**：使用者可以直接用 `SimpleSearchCriteria`，也可以继承 `BaseSearchCriteria` 自定义，不再返回 null。

### 4. BaseDaoJdbiImpl - 提取辅助类

**现状**：`BaseDaoJdbiImpl`（306 行）包含多种职责：
- DAO 接口方法实现
- 参数绑定逻辑（Query 和 Update 两种场景）
- SQL 缓存管理
- 结果映射

**改进**：
- 提取 `ParameterBinder` - 通用参数绑定工具，提供：
  - `bindParameters(Query, params, listParams)`
  - `bindParameters(Update, params, listParams, beanParams)`
  - 复用逻辑，可单独测试
- 提取 `SqlCache` - SQL 缓存封装，包装 `ConcurrentHashMap`，提供 `computeIfAbsent` 简化调用
- `BaseDaoJdbiImpl` 保持原有抽象 DAO 职责不变，只委派给新类

### 5. 分页模块 - 保持现状

项目未依赖 Spring Data，因此保持现有的自定义分页实现不变：
- `page/` 包结构不变
- 所有分页相关类保留，不做删除
- 当前实现已经足够清晰，无需重构

## 代码行数估算

| 类 | 当前行数 | 改进后总行数 |
|---|---|---|
| TableDescription | 113 | ~60 |
| SqlGenerator | 240 | ~80 (外观) |
| SqlGenerator 拆分出的新类 | - | ~180 (每个 20-40 行) |
| BaseDaoJdbiImpl | 306 | ~220 |
| 新提取类 | - | ~80 |
| SearchCriteria 改进 | 27 | 新增 ~60 |
| **总计** | **686** | **~680** |

保持总代码行数相近，但职责划分更清晰。

## 兼容性保证

- 所有公有 API 方法签名保持不变
- 现有使用者无需修改代码
- 只有内部实现重构，不影响外部使用
- Java Module Version 保持 11 不变

## 测试

- 保留现有的端到端测试
- 新增类编写单元测试
- 验证重构后功能完全一致

## 下一步

执行实现计划，按步骤逐步重构。
