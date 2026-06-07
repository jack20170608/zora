# zora-rocksdb

> 基于 RocksDB 的嵌入式图存储工具。
>
> 当前提供生产化的属性图（property graph）引擎 `GraphStore`：双向邻接、顶点与边的可索引属性（等值 + 范围）、悲观行锁事务、显式事务句柄、可调锁超时。后续将在此模块下沉淀其他 RocksDB 工具（时间序列索引、Tape 流水等）。

---

## 1. 快速上手

### 1.1 依赖

```xml
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

### 1.2 5 分钟示例

```java
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;
import top.ilovemyhome.zora.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.rocksdb.graph.store.*;

try (GraphStore store = new GraphStore("/var/data/social-graph")) {

    int PERSON = 1, KNOWS = 10;

    // 写顶点（属性索引自动维护）
    store.addVertex(new Vertex(1L, PERSON)
        .withProperty("name", "Alice").withProperty("age", 30));
    store.addVertex(new Vertex(2L, PERSON)
        .withProperty("name", "Bob").withProperty("age", 25));

    // 写边（双向邻接 + 边属性 3 向索引自动维护）
    store.addEdge(new Edge(1L, 2L, KNOWS).withProperty("since", "2020"));

    // 顶点属性等值查询
    List<Vertex> alices = store.findVerticesByProperty(PERSON, "name", "Alice");

    // 顶点属性范围查询（数字 / 字符串 / 日期都可，要求两端同类型）
    List<Vertex> youngsters = store.findVerticesByPropertyRange(PERSON, "age", 18, 30);

    // 边属性等值 / 范围查询
    List<Edge> e2020   = store.findEdgesByProperty(KNOWS, "since", "2020");
    List<Edge> aliceOut2020 = store.findOutEdgesByProperty(1L, KNOWS, "since", "2020");

    // 邻居遍历（出边 → 顶点点查）
    List<Vertex> aliceFriends = store.getNeighbors(1L, KNOWS);
}
```

---

## 2. 核心特性

| 能力 | API | 说明 |
|---|---|---|
| 顶点 CRUD | `addVertex / getVertex / removeVertex` | `getVertex(long)` 借助 vertexId → typeId 反查表是 O(log N) 点查 |
| 边 CRUD | `addEdge / getEdge / removeEdge` | 一次写入双向邻接 + 3 份属性索引，原子提交 |
| 邻接遍历 | `getOutEdges / getInEdges / getNeighbors` | 前缀扫描，O(度) |
| 顶点属性索引 | `findVerticesByProperty[Range]` | propId + 保序值编码，等值/范围统一走 seek+scan |
| 边属性全局索引 | `findEdgesByProperty[Range]` | 跨整个图按 (eType, prop, value) 查 |
| 边属性按端点索引 | `findOut/InEdgesByProperty[Range]` | (src 或 dst) + 属性双约束直接一次 seek 完事 |
| 显式事务 | `beginTransaction() → GraphTxn` | read-modify-write 全程原子；非线程安全，每线程一个 |
| 行级悲观锁 | `GraphTxn.getVertex/getEdge` 内部 `getForUpdate` | 同一 vertex/edge 上的并发事务串行化，不同 key 之间并行 |
| 可配 | `GraphStoreOptions` | 锁超时、死锁检测、是否 fsync 每次提交 |

---

## 3. 存储分层

RocksDB 内 6 个 Column Family，按访问模式物理隔离：

```
┌─────────────────────────────────────────────────────────────────────┐
│ default        计数器 (__vertex_counter__, __prop_id_counter__)     │
├─────────────────────────────────────────────────────────────────────┤
│ cf_vertex      V | typeId(4) | vertexId(8)              → JSON      │
├─────────────────────────────────────────────────────────────────────┤
│ cf_vertex_type vertexId(8 BE)                           → typeId(4) │
│                "反查表"：让 getVertex(long) 不必扫全表                │
├─────────────────────────────────────────────────────────────────────┤
│ cf_edge        OUT: E | src | eType | 'O' | dst        → JSON      │
│                IN : E | dst | eType | 'I' | src        → JSON      │
│                同一逻辑边写两份，双向邻接前缀扫一步到位                 │
├─────────────────────────────────────────────────────────────────────┤
│ cf_index       I | typeId | propId | encVal | vertexId → ∅          │
│                顶点二级索引（等值 + 范围）                            │
├─────────────────────────────────────────────────────────────────────┤
│ cf_edge_index  J|P|eType|propId|encVal|src|dst         → ∅  ← 全局  │
│                J|S|src |eType|propId|encVal|dst        → ∅  ← 按src │
│                J|D|dst |eType|propId|encVal|src        → ∅  ← 按dst │
│                一条边一个属性写 3 份；查询全是单次 seek+scan           │
├─────────────────────────────────────────────────────────────────────┤
│ cf_schema      S|N|<propName>                          → propId(4) │
│                S|I|<propId>                            → propName   │
│                propName ↔ propId 字典，V 与 E 共享同一命名空间        │
└─────────────────────────────────────────────────────────────────────┘
```

**保序值编码** 让 RocksDB 的 unsigned lex 顺序 = 值的自然顺序：

| Java 类型 | tag | fixedValue | 字节数 |
|---|---|---|---|
| `Integer/Long` | `0x01` | `v ^ 0x80…00` 后 8 字节 BE（符号位翻转） | 9 |
| `Float/Double` | `0x02` | IEEE 754 bits `^ ((bits>>63) \| 0x80…)`（正翻最高位，负全翻） | 9 |
| `Boolean` | `0x03` | `0x00 / 0x01` | 2 |
| `String` | `0x04` | UTF-8，`0x00` 转义为 `0x00 0xFF`，以 `0x00 0x00` 结尾 | 变长 |

---

## 4. 显式事务（`GraphTxn`）

`GraphStore` 上每个 mutator 都是 1-shot 事务的 thin wrapper；调用方无需感知。但当你需要"**读 → 算 → 写**"在并发下不丢更新时，必须用显式事务：

```java
// 例：给同一个顶点累加 marker 属性
try (GraphTxn t = store.beginTransaction()) {
    Vertex v = t.getVertex(PERSON, 1L);           // ← 加写锁
    Vertex updated = v.withProperty("marker_X", true);
    t.addVertex(updated);                          // ← 不会被并发覆盖
    t.commit();                                    // ← 必须显式 commit
}
```

`t.getVertex` 内部用 `getForUpdate` 加行级写锁；其他事务对**同一 (typeId, vertexId)** 的写要排队等本事务 commit/rollback，但**别的 vertex/edge 完全并行**。

`GraphTxn` 不暴露 `find*` 索引查询——索引迭代器锁定的范围会爆炸成 contention 热点。若事务里确需按属性筛，先用 `store.find*` 拿候选 id 列表，再 `t.getVertex(id)` 逐个加锁。

可见性矩阵：

| 操作 | 看到什么 |
|---|---|
| `store.getVertex / find*`（事务外） | 最新已提交快照，lock-free |
| `t.getVertex / t.getEdge`（事务内） | 自己未提交写入 + 最新已提交（read-your-own-writes） |
| 别的线程的 `store.find*` 在 `t` 未 commit 时 | 看不到 `t` 的 pending 写入 |
| 别的线程的 `t2.getVertex(同 key)` | 阻塞，等 `t.commit()` 或 `t.rollback()` |

`close()` 时如果既没 commit 也没 rollback → **隐式 rollback + WARN**，防止 try-with-resources 漏 commit 静默挂事务。

---

## 5. 配置（`GraphStoreOptions`）

```java
GraphStoreOptions options = GraphStoreOptions.builder()
    .lockTimeoutMillis(500)   // 默认 1000ms；0 = fail-fast；-1 = 永久等待
    .deadlockDetect(true)     // 默认 true：循环等待时一方拿 Status.Busy 退出
    .syncWrites(false)        // 默认 false：相信 OS page cache；true 则每次 commit fsync
    .build();

try (GraphStore store = new GraphStore("/var/data/graph", options)) {
    // ...
}
```

什么时候调：

| 想要的效果 | 配法 |
|---|---|
| 短任务、宁可立刻失败也不愿等 | `lockTimeoutMillis(0)` |
| 锁热点严重，但不接受死锁挂死 | 保留默认 `deadlockDetect=true`，把 `lockTimeoutMillis` 调大 |
| 单机金融场景，要求断电后不丢已 commit | `syncWrites(true)`（吞吐会下降 ~10×） |

详细默认值与语义见 `GraphStoreOptions` JavaDoc。

---

## 6. 性能权衡（你要知道的写放大）

| 操作 | 内部写次数 |
|---|---|
| `addVertex(k 个属性)` | 1（主存储 JSON） + 1（vertexType 反查，仅首次插入） + k（顶点索引） |
| `addEdge(k 个属性)` | 2（双向邻接） + **3k**（3 个 flavor 的边索引） |
| `removeVertex(度 = e, p 个顶点属性, 平均 k 个边属性)` | 1 + 1 + e + 3·∑边属性 + p |

边索引写放大 3 倍是为了让"按 src 端点 + 属性"和"按 dst 端点 + 属性"都能退化为单次 prefix scan，无须用户侧 intersection。对"边比点多 1~2 个数量级 + 频繁按属性筛"的场景值得；纯写多读少的场景可以等以后做"可关闭某个 flavor"的开关。

读操作 (`getVertex / getEdge / find*` / 邻接遍历) 全部 lock-free，看最新已提交快照。

---

## 7. 模块定位

| 模块 | 角色 |
|---|---|
| **zora-rocksdb** | 生产级 jar，对外稳定 API，下游业务可直接依赖 |
| zora-poc/zora-persistence-poc/zora-rocksdb-poc | 探索沙盒：RocksDB 各特性样例与基准；不保证 API 稳定 |

设计演进与决策细节看 PoC 文档：

| 文档 | 主题 |
|---|---|
| [`24-rocksdb-graph-index-refactor.md`](../zora-poc/zora-persistence-poc/zora-rocksdb-poc/docs/24-rocksdb-graph-index-refactor.md) | Vertex 索引：propId 字典 + 保序编码 + range |
| [`25-rocksdb-edge-property-index.md`](../zora-poc/zora-persistence-poc/zora-rocksdb-poc/docs/25-rocksdb-edge-property-index.md) | Edge 索引 + TransactionDB 化 |
| [`26-rocksdb-explicit-transaction.md`](../zora-poc/zora-persistence-poc/zora-rocksdb-poc/docs/26-rocksdb-explicit-transaction.md) | 显式事务 API（GraphTxn）|

模块入口设计文档：[`docs/FEATURE-12-zora-rocksdb.md`](../docs/FEATURE-12-zora-rocksdb.md)。

---

## 8. 测试与基线

```bash
# 单元 + 集成 + 并发
mvn -pl zora-rocksdb test

# 性能基线（粗粒度，不是 JMH）
mvn -pl zora-rocksdb test -Dtest=GraphStoreBenchmarkTest
```

当前测试矩阵：

| 套件 | 用例 |
|---|---|
| `GraphStoreTest` | 35（CRUD / 双向邻接 / 顶点&边索引 / 范围 / 反查表 / 并发自洽） |
| `GraphTxnTest` | 10（commit/rollback/隐式 rollback/read-your-own-writes/可见性/并发 marker 累加） |
| `GraphStoreOptionsTest` | 4（默认值、Builder 往返、`lockTimeoutMillis(0)` fail-fast、syncWrites 持久化） |
| `GraphStoreBenchmarkTest` | 7（写吞吐 / 邻居遍历 / 索引查询 / 混合负载 / 删除带边顶点） |
| **合计** | **56**，全绿 |

---

## 9. 限制与已知遗留

- **单进程**：RocksDB 本身只允许一个进程打开同一目录。
- **GraphTxn 非线程安全**：一个事务对应一个线程；跨线程共享请自行同步。
- **`addVertex` 是整对象覆盖**，不是字段级合并。多线程独立调用同一 id 仍可能丢更新——这是设计取舍，要 field-level merge 请走 `GraphTxn` 自己 `getVertex → withProperty → addVertex`。
- **`NaN` 浮点**：`Double.doubleToLongBits` 标准化后落在 +∞ 之后；若需严格排除，调用前自行 `Double.isNaN` 拦截。
- **死锁检测**：默认开启，循环等待一方会拿到 `Status.Busy`；GraphStore 当前不做自动重试，调用方自行 catch + retry。

---

## 10. 路线图

- [ ] `RocksDBClient` 通用 KV API（脱离 graph 概念），把 PoC 中验证过的批量写 / 迭代器 / 备份恢复能力沉淀
- [ ] `TimeIndexStore`、`FilterableFileStore`（PoC 已经迭代几版）
- [ ] 可选关闭某些 edge index flavor，给"按 src 查"不强的工作负载省写放大
- [ ] GraphTxn 自动重试包装器（捕获 `Status.Busy` 退避重试）
