# zora-rocksdb - Embedded RocksDB Utilities

zora-rocksdb 是 zora 框架中基于 RocksDB 的嵌入式存储工具模块。当前提供一个生产化的属性图（property graph）存储引擎 `GraphStore`，未来可继续在此模块下沉淀其他 RocksDB 使用场景（时间序列索引、Tape 流水等）。

## 功能特性

- **顶点 / 边 CRUD**：基于 Column Family 物理隔离的主存储。
- **双向邻接索引**：每条逻辑边写两份 Key（OUT/IN），出/入边查询均为单次前缀扫描。
- **属性二级索引**
  - 等值查询：`findVerticesByProperty`
  - 范围查询：`findVerticesByPropertyRange`（数字、浮点、字符串均支持闭区间）
- **属性字典**：propName ↔ propId 持久化在独立 CF，索引 Key 用 4 字节 propId 取代哈希，消除冲突。
- **保序值编码**：long 翻符号位、double 保序、string 用 `0x00→0x00 0xFF` 转义并 `0x00 0x00` 终止，让 RocksDB 的 unsigned lex 顺序等价于值的自然顺序。
- **写入正确性**：`addVertex` 自动 diff 旧值，删除过期索引项；`WriteBatch` 全部 try-with-resources。

## 依赖引入

```xml
<dependency>
    <groupId>top.ilovemyhome</groupId>
    <artifactId>zora-rocksdb</artifactId>
    <version>${zora.version}</version>
</dependency>
```

## 快速使用

```java
try (GraphStore store = new GraphStore("/var/data/social-graph")) {

    int personType = 1;
    int knowsType  = 10;

    // 写入顶点（自动维护属性索引）
    store.addVertex(new Vertex(1L, personType)
        .withProperty("name", "Alice")
        .withProperty("age", 30));

    store.addVertex(new Vertex(2L, personType)
        .withProperty("name", "Bob")
        .withProperty("age", 25));

    // 写入边（自动维护双向邻接）
    store.addEdge(new Edge(1L, 2L, knowsType).withProperty("since", "2020"));

    // 等值查询
    List<Vertex> alices = store.findVerticesByProperty(personType, "name", "Alice");

    // 范围查询
    List<Vertex> youngsters = store.findVerticesByPropertyRange(personType, "age", 18, 30);

    // 邻居遍历
    List<Vertex> aliceFriends = store.getNeighbors(1L, knowsType);
}
```

## 存储布局

| Column Family | 用途 | Key |
|---|---|---|
| `default`    | 元数据计数器 | `__vertex_counter__` / `__prop_id_counter__` |
| `cf_vertex`  | 顶点主存储 | `V \| typeId(4) \| vertexId(8)` |
| `cf_edge`    | 边主存储 + 邻接索引 | `E \| firstId(8) \| edgeType(4) \| dir(1) \| secondId(8)` |
| `cf_index`   | 属性二级索引 | `I \| typeId(4) \| propId(4) \| encodedValue \| vertexId(8)` |
| `cf_schema`  | 属性名 ↔ propId 字典 | `S \| N \| <propName>` 与 `S \| I \| <propId>` |

更多设计细节见 zora 根目录 `docs/FEATURE-12-zora-rocksdb.md` 与 PoC 文档 `zora-poc/zora-persistence-poc/zora-rocksdb-poc/docs/24-rocksdb-graph-index-refactor.md`。

## 测试与基准

```bash
# 单元 + 集成测试
mvn -pl zora-rocksdb test

# 性能基线
mvn -pl zora-rocksdb test -Dtest=GraphStoreBenchmarkTest
```

## 模块定位

| 模块 | 角色 |
|---|---|
| `zora-rocksdb` | **生产级 jar**：供下游模块/服务直接依赖的 RocksDB 工具集。 |
| `zora-poc/zora-persistence-poc/zora-rocksdb-poc` | 探索性 PoC：RocksDB 各种特性的样例与基准；不对外提供 API 稳定性保证。 |
