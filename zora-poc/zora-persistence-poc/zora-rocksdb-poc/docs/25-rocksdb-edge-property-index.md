# 25 - Edge 属性索引与悲观事务化

> 状态：已落地
> 关联模块：`zora-rocksdb`
> 上一篇：`24-rocksdb-graph-index-refactor.md`（vertex 属性索引重构）
> 关联代码：`KeyCodec.java`、`GraphStore.java`、`GraphStoreTest.java`

## 1. 重构动机

`24` 号文档把 vertex 的二级索引升级到了生产水位，但 edge 当时只有"双向邻接表"，**properties 只在 JSON 主存储里**。下游一旦问出 "找出所有 `since='2020'` 的 KNOWS 边"、"找 Alice 的所有 `weight ∈ [5, 15]` 的出边"，就只能拉全表/拉全邻居再扫一遍。

同时 vertex 的 `addVertex` 用的是 `WriteBatch` —— 单线程下原子，但**并发场景下"读 prev → 写 batch"中间存在窗口**，会导致 last-writer-wins 时的 index 与主存储不一致：A 读旧值 prev → B 完整覆盖 → A 用 prev diff 出"该删的旧索引项"，但这些项已经是 B 写入的"新值"，A 一删，索引就指向了一个不存在的值。

本次重构同时解决这两个问题：

1. 给 edge 上属性索引；
2. 把 GraphStore 全面迁到 `TransactionDB`（悲观锁），让 "读 prev → 写新"在事务保护下原子。

## 2. cf_edge_index 的三类 Key

新增独立的 column family `cf_edge_index`，propId 命名空间继续和 vertex 共享 `cf_schema`（**方案 C**：语义统一、物理隔离）。同一个边属性写 **3 份** key，每份服务一种查询模式：

```
J | P | eType(4) | propId(4) | encVal      | srcId(8) | dstId(8)     ← 全局
J | S | srcId(8) | eType(4)  | propId(4)   | encVal   | dstId(8)     ← 按 src
J | D | dstId(8) | eType(4)  | propId(4)   | encVal   | srcId(8)     ← 按 dst
```

| flavor | 头部 | 服务的查询 API |
|---|---|---|
| `J/P` 全局 | `eType, propId` | `findEdgesByProperty / findEdgesByPropertyRange` |
| `J/S` src-keyed | `src, eType, propId` | `findOutEdgesByProperty / findOutEdgesByPropertyRange` |
| `J/D` dst-keyed | `dst, eType, propId` | `findInEdgesByProperty / findInEdgesByPropertyRange` |

**关键设计点：**

- **首字节用 `J`** 而非 `I`（vertex 索引），保证不会出现 vertex 索引和 edge 索引混在同一区间被误扫。`J` 是 `I` 的邻居字符，肉眼也很容易区分。
- **flavor 字节紧跟在首字节后**，使得 `cf_edge_index` 内部按 flavor 三段隔离，三种查询互不污染。
- **tail 必须带"另一端 id"**：同一 `(eType, propId, value)` 可能对应多条边，必须用 src/dst 把它们区分开；否则不同边的同一属性会互相覆盖。
  - 全局形态尾巴是 `srcId + dstId`（16 字节）；
  - 端点形态尾巴是另一端 `8 字节`。

### 2.1 端点查询为什么需要单独的 flavor？

如果只保留全局 flavor `J|P|...`，要查 "Alice 的所有 `since='2020'` 出边" 就得：

1. 用全局索引扫出所有 `(eType=KNOWS, since='2020')` 的边；
2. 在用户代码里过滤 `srcId == 1L`。

当 Alice 只有 3 条边、但全图有 100 万条 KNOWS 边时，这是 100 万次 prefix-scan + 100 万次 filter。

引入 `J|S|<srcId>|...` 后，查询前缀直接收窄到 Alice 自己，**一次 seek + 3 步 next** 完事。**写放大 3 倍 ↔ 端点查询从 O(全图) 降到 O(Alice 的出边数)**，对边密集型负载是非常合算的交换。

### 2.2 范围查询的端点形态

对每个 flavor 都暴露 `RangeLowerBound / RangeUpperBound` 两个方法，规则同 vertex range：

- `LowerBound`：把 trailing 部分留 0
- `UpperBound`：把 trailing 部分填 `0xFF`

于是 `findOutEdgesByPropertyRange(srcId, eType, "weight", 5, 15)` 这种"端点 + 属性范围"双约束查询只需要单次 seek + 单调扫到 upperBound，无须任何用户侧 intersection。

## 3. Edge upsert 的 diff 语义

借鉴 vertex `addVertex`，`addEdge` 现在也是 diff upsert：

```
oldEdge = txn.getForUpdate(cfEdge, outKey)            // 上锁 + 读旧
txn.put(cfEdge, outKey, JSON(new))                    // 双向主存储覆盖
txn.put(cfEdge, inKey,  JSON(new))
for prop in oldEdge.properties:
    if oldEdge[prop] != newEdge[prop]:
        delete 3-way index entries for old value      // 删旧
for prop in newEdge.properties:
    if oldEdge[prop] != newEdge[prop]:
        put 3-way index entries for new value         // 写新
txn.commit()
```

这样：
- "改值"的边不会留下指向旧值的索引（消除幽灵）；
- "没变化"的属性不重复写（减小写放大）；
- 全程在一个 `Transaction` 里，主存储 / 双向邻接 / 索引 / 字典写入要么全成功要么全回滚。

`removeEdge` 同样会扫一遍 properties 把 3 份索引项删干净；`removeVertex` 删边时不再只看 `cf_edge` 的 key，而是 `decode(it.value())` 拿到 Edge 对象后调 `deleteEdgeIndexEntries`，防止删点时把属性索引留下。

## 4. 从 RocksDB 切到 TransactionDB

### 4.1 为什么选 TransactionDB（悲观锁）

| 方案 | 单进程并发安全 | 语义 | 后台压力 |
|---|---|---|---|
| 普通 `RocksDB` + `WriteBatch` | ❌ "读旧→写新"有窗口，丢更新 | last-writer-wins，但 index 可能 stale | 最低 |
| `OptimisticTransactionDB` | ✅ commit 时检测冲突 | 抛 `Status.Busy`，需要应用层重试 | 高并发下 retry storm |
| `TransactionDB`（**已选用**） | ✅ getForUpdate 直接加行锁 | 冲突阻塞，对调用方完全透明 | 锁 contention，但 row-level |

PoC / 工具库定位下，"调用方零感知"的悲观锁路径明显更友好；高并发热点 row 上才会出现锁等待，可通过 `setLockTimeout` 调。

### 4.2 改造点

```java
// 字段
- private final RocksDB db;
+ private final TransactionDB db;
+ private final ReadOptions readOptions;

// open
- this.db = RocksDB.open(dbOptions, dbPath, cfDescriptors, cfHandles);
+ TransactionDBOptions txnDbOptions = new TransactionDBOptions();
+ this.db = TransactionDB.open(dbOptions, txnDbOptions, dbPath, cfDescriptors, cfHandles);

// addVertex / addEdge / removeVertex / removeEdge / nextVertexId
- try (WriteBatch batch = new WriteBatch()) {
-     prev = getXxx(...);              // 事务外读，有窗口！
-     batch.put / batch.delete ...
-     db.write(writeOptions, batch);
+ try (Transaction txn = db.beginTransaction(writeOptions)) {
+     prev = txn.getForUpdate(readOptions, cf, key, true);  // 上锁 + 读
+     txn.put / txn.delete ...
+     txn.commit();
  }
```

字典层（`resolvePropId`）也搬进事务：propId 计数器 `__prop_id_counter__` 用 `getForUpdate` + `put`，让"两个线程首次见到不同属性名"时不会拿到同一个 id。

### 4.3 行锁的粒度

`TransactionDB` 默认是 row-level lock，锁只覆盖事务里 `getForUpdate` 过的具体 key：

- 改 vertex 1 → 仅锁 `V|type|1` + 该顶点用到的字典/计数器 key；
- 改 edge (1→2) → 锁两条邻接 key + 字典/计数器；
- 不同顶点 / 不同边的并发写**完全无阻塞**。

读操作 (`getVertex / getEdge / findVerticesByProperty / findEdgesByProperty / ...`) 走的是非事务读，**lock-free**、看最新已提交快照。

### 4.4 测试如何验证

新增 2 个并发测试：

| 测试 | 目的 |
|---|---|
| `shouldKeepIndexConsistentUnderConcurrentChurn` | 8 线程并发改同一个顶点的 `city` 属性，事后断言：最终 vertex 的 city 是 5 个候选之一，**用该值查索引能找到 vertex，其他候选值不会返回此 vertex**——确认"主存储 vs 索引"的最终一致 |
| `shouldGenerateUniqueIdsUnderContention` | 8 线程各 200 次 `nextVertexId()`，断言 1600 个 id 全部唯一——确认计数器在事务内 read-modify-write 没漏锁 |

注意：**事务并不能让 `addVertex` 从"整对象覆盖"变成"字段级合并"**。`addVertex(v)` 写入的是 `v` 的完整 JSON 与全套 properties；并发下仍然 last-writer-wins。事务保护的是 GraphStore **内部**"读 prev + diff index + 写新"这一段不会与其他事务交错，从而**保证不会出现"主存储已更新但索引指向旧值"的损坏**。如果上层需要"字段级 merge"语义，那应该在调用方先 `getVertex` 再合并属性再 `addVertex`——并且整个合并过程外加自己的锁，或者直接走未来要加的"显式事务 API"（见 §6 路线图）。

## 5. 写放大与性能权衡

| 操作 | 旧版（WriteBatch） | 新版（Transaction + 3-way edge index） |
|---|---|---|
| `addVertex` (k props) | 1 主 + k 索引 = 1+k | 同上 + 1 行锁 |
| `addEdge` (k props) | 2 主（OUT/IN） | 2 主 + **3k 索引** + 2 行锁 |
| `removeVertex` (e 条边, p 顶点 props) | 1 + e（边主存储） + p（顶点索引） | 1 + e（边主存储） + **3·∑k_e（边索引）** + p（顶点索引） + 锁 |
| `findOutEdgesByProperty(src, ...)` | 不存在 → 邻接全扫 + filter | seek + scan，O(命中数) |

写放大的代价是真实的（每条边 1 个属性 = 3 个索引项），换取 4 类查询的零 intersection 单 seek。对"边比点多 1~2 个数量级、且经常按属性筛"的场景值得。

## 6. 已知遗留与路线图

| 项 | 现状 | 后续 |
|---|---|---|
| 显式事务 API | GraphStore 把事务藏在每个方法里，调用方无法把"读 + 改 + 写"包成一个事务 | v1 增加 `try (Txn t = store.beginTransaction()) { ... }` 形式，配合 `t.addVertex / t.findVerticesByProperty` |
| 字段级 merge | `addVertex` 仍是整对象覆盖 | v1 配合显式事务，或新增 `mergeVertex(id, ChangeSet)` |
| `NaN` 浮点 | `Double.doubleToLongBits` 标准化为单一 NaN bit-pattern，落在 +∞ 之后 | 如需排除，`encodeDouble` 入口判 `Double.isNaN` |
| `getVertex(long)` fallback 全表扫 | 主要给"按边 dst 取邻居"用 | v2 加一张 `vertexId → typeId` 反查表，彻底干掉 |
| 跨进程并发 | RocksDB 本身只允许单进程 open | 不在此模块解决 |

## 7. 测试矩阵

| 套件 | 用例数 | 关注 |
|---|---|---|
| `GraphStoreTest` | **33** | + 8 个 edge 索引 + 2 个并发 + 2 个 endpoint range；原 vertex 测试全部保留 |
| `GraphStoreBenchmarkTest` | 7 | 写吞吐 / 邻居遍历 / 索引查询 / 混合负载等基线，未回归 |

```
mvn -pl zora-rocksdb test  →  Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
mvn validate               →  Reactor 24 modules SUCCESS
```

## 8. 文件变更清单

```
zora-rocksdb/src/main/java/.../graph/codec/KeyCodec.java
    + PREFIX_INDEX_EDGE = 'J'
    + Edge index 3 flavors + 4 个 range bound 方法 + 3 个 decode 方法

zora-rocksdb/src/main/java/.../graph/store/GraphStore.java
    * RocksDB → TransactionDB；构造器 +TransactionDBOptions / +ReadOptions
    + cfEdgeIndex 字段 + 'edge_index' CF
    * addVertex / removeVertex / addEdge / removeEdge / nextVertexId：
      WriteBatch → Transaction + getForUpdate
    * resolvePropId：接受 Transaction 参数，全部在事务内 read-modify-write
    + putEdgeIndexForProperty / deleteEdgeIndexForProperty /
      deleteEdgeIndexEntries (3 个 helper)
    + findEdgesByProperty / findEdgesByPropertyRange /
      findOutEdgesByProperty / findInEdgesByProperty /
      findOutEdgesByPropertyRange / findInEdgesByPropertyRange (6 个新 API)

zora-rocksdb/src/test/java/.../graph/store/GraphStoreTest.java
    + 8 个 edge 索引用例（全局/范围/端点等值/端点范围/更新清理/删除清理/字典共享/重启）
    + 2 个并发测试（索引自洽 + id 唯一）
```
