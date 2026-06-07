# FEATURE-12 zora-rocksdb 模块

> 状态：已落地（首次发布）
> 关联模块：`zora-rocksdb`
> 关联 PoC：`zora-poc/zora-persistence-poc/zora-rocksdb-poc`

## 1. 背景

在 `zora-poc/zora-persistence-poc/zora-rocksdb-poc` 中我们已经把 RocksDB 的各类基础特性（CF、WriteBatch、Iterator、Snapshot、Transaction、Backup、Statistics 等）和一个完整的属性图存储引擎跑通。其中 `GraphStore` 经过一轮[索引重构](../zora-poc/zora-persistence-poc/zora-rocksdb-poc/docs/24-rocksdb-graph-index-refactor.md)（propId 字典、保序值编码、范围查询、diff upsert、回表带 typeId、try-with-resources 全覆盖）后，已达到可以对外暴露给下游业务模块直接复用的水平。

PoC 模块定位是"探索 + 文档"，本身放在 `src/test/java`，对外没有 jar 产物；不适合作为生产依赖。因此本次新增 `zora-rocksdb` 顶级模块，作为 zora 工具栈中"RocksDB 嵌入式存储工具"的正式入口。

## 2. 模块定位

| 模块 | 角色 | 代码位置 |
|---|---|---|
| `zora-rocksdb` | **生产级 jar**，对外稳定 API | `src/main/java/top/ilovemyhome/zora/rocksdb/...` |
| `zora-poc/.../zora-rocksdb-poc` | 探索性 PoC，不保证 API 稳定性 | `src/test/java/.../poc/persistence/rocksdb/...` |

两者不再共享代码：PoC 持续作为 RocksDB 特性学习与基准沙盒；`zora-rocksdb` 只承诺对外稳定的工具集，PoC 中验证成熟的内容会陆续在此模块沉淀。

## 3. 首期内容

`zora-rocksdb` 1.0.1-SNAPSHOT 首版包含的全部公共 API：

```
top.ilovemyhome.zora.rocksdb.graph
├── codec
│   ├── KeyCodec      // Key 编码/解码（vertex / edge / index）
│   └── ValueCodec    // Jackson JSON 值编解码
├── model
│   ├── Vertex        // 顶点
│   ├── Edge          // 有向边
│   └── Property      // 保序 + tag 编码（long/double/bool/string）
└── store
    └── GraphStore    // 5-CF 属性图存储引擎（含字典 CF）
```

核心能力：

- 顶点 / 边 CRUD（`addVertex / getVertex / removeVertex / addEdge / getEdge / removeEdge`）
- 双向邻接遍历（`getOutEdges / getInEdges / getNeighbors`）
- 属性二级索引：
  - 等值：`findVerticesByProperty`
  - 范围（左右闭区间）：`findVerticesByPropertyRange`
- 自动维护属性字典（`cf_schema`）、保序 + tag 值编码、diff upsert、回表精确点查、所有 `WriteBatch` 全 try-with-resources。

详细的索引设计与 Key 布局见 [`zora-poc/zora-persistence-poc/zora-rocksdb-poc/docs/24-rocksdb-graph-index-refactor.md`](../zora-poc/zora-persistence-poc/zora-rocksdb-poc/docs/24-rocksdb-graph-index-refactor.md)。

## 4. 工程配置

### 4.1 依赖管理

- `zora-dependencies` 新增统一管理：`org.rocksdb:rocksdbjni:9.10.0`
- `zora-bom` 加入条目，下游可通过 BOM `import` 统一拉取版本

### 4.2 模块 pom 依赖

| 依赖 | 作用 |
|---|---|
| `org.rocksdb:rocksdbjni` | RocksDB JNI 绑定 |
| `com.fasterxml.jackson.core:jackson-databind` | 顶点 / 边 JSON 值编解码 |
| `org.slf4j:slf4j-api` | 日志门面 |
| `slf4j-simple` / `junit-jupiter-*` / `assertj` / `mockito-*` | 测试 |

### 4.3 资源约定

- `metadata/metadata.json`：按 CLAUDE.md 规范填充 groupId / artifactId / description / version / scmUrl，由 `metadata` resource 自动 filter
- `src/test/resources/simplelogger.properties`：统一日志格式

## 5. 测试

| 测试套件 | 用例数 | 覆盖 |
|---|---|---|
| `GraphStoreTest` | 21 | 顶点 / 边 CRUD、双向邻接、等值查询、字符串前缀防误匹配、整数/浮点/字符串/负数范围查询、字典持久化、社交图端到端 |
| `GraphStoreBenchmarkTest` | 7 | 顶点写、边写、邻居遍历扩展性、入边遍历、属性索引查询、混合负载、删除带边顶点 |

构建结果：

```
mvn -pl zora-rocksdb test
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

完整 reactor `mvn validate` 24 个模块（含新模块）全部 SUCCESS。

## 6. 下游使用

```xml
<!-- 通过 BOM 统一版本 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>top.ilovemyhome.zora</groupId>
            <artifactId>zora-bom</artifactId>
            <version>${zora.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>top.ilovemyhome.zora</groupId>
        <artifactId>zora-rocksdb</artifactId>
    </dependency>
</dependencies>
```

调用样例：

```java
try (GraphStore store = new GraphStore("/var/data/social-graph")) {
    int personType = 1, knowsType = 10;

    store.addVertex(new Vertex(1L, personType)
        .withProperty("name", "Alice").withProperty("age", 30));
    store.addVertex(new Vertex(2L, personType)
        .withProperty("name", "Bob").withProperty("age", 25));
    store.addEdge(new Edge(1L, 2L, knowsType).withProperty("since", "2020"));

    List<Vertex> youngsters = store.findVerticesByPropertyRange(personType, "age", 18, 30);
    List<Vertex> aliceFriends = store.getNeighbors(1L, knowsType);
}
```

## 7. 后续 Roadmap

| 阶段 | 内容 |
|---|---|
| v0 (本次) | 搬运 GraphStore 全部代码 + 索引重构成果 |
| v1 | 引入 `OptimisticTransactionDB`，让 `addVertex` 的 "读 old → 写 batch" 真正乐观事务化；多进程安全的 propId 分配 |
| v2 | 抽出与 graph 解耦的 `RocksDBClient` 基础封装（KV API、批量写、迭代器、备份/恢复），把 PoC 中验证过的能力沉淀过来 |
| v3 | 新增 `TimeIndexStore`（时间索引）、`FilterableFileStore`（文件元数据）等 PoC 已经迭代过几版的工具 |
