# 26 - 显式事务 API：GraphTxn

> 状态：已落地
> 关联模块：`zora-rocksdb`
> 上一篇：`25-rocksdb-edge-property-index.md`（cf_edge_index + TransactionDB 化）
> 关联代码：`GraphStore.java`、`GraphTxn.java`、`GraphTxnTest.java`

## 1. 重构动机

`25` 号文档把 GraphStore 切换到 `TransactionDB`，让每个 mutator 方法（`addVertex / addEdge / removeVertex / removeEdge / nextVertexId`）内部用一个事务保护"读 prev + diff index + 写新"这段原子。这一步解决了 **GraphStore 内部** 的写一致性问题，但留下一个调用方层面的痛点：

```java
// 调用方想 "读 -> 改 -> 写"，但 read 和 write 是两次独立调用
Vertex v = store.getVertex(personType, 1L);          // 事务 A 外
Vertex merged = v.withProperty("marker_N", true);     // 计算在外
store.addVertex(merged);                              // 事务 B 内
```

并发下两个线程都执行这段代码：

1. 两个线程都读到了同一个 `prev`（没有 marker）；
2. 各自算出"prev + marker_X"和"prev + marker_Y"；
3. 各自起独立事务写回——后写的覆盖先写的，**整对象覆盖语义 → 丢更新**。

事务保护的是"GraphStore 内部步骤"原子性，**保护不到"调用方在事务之外做的读"**。这是 25 号文档 §4.4 明确标注的边界。

本次重构引入 **显式事务句柄 `GraphTxn`**，让调用方能把自己的"读 → 算 → 写"全部包进一个事务里，由 RocksDB 的行锁串行化。

## 2. API 形状

```
GraphStore
├── beginTransaction(): GraphTxn          ← 新增
├── addVertex(Vertex)         ─┐
├── removeVertex(t, id)        │ 1-shot 内部 wrapper：开 GraphTxn → 操作 → commit
├── addEdge(Edge)              │
├── removeEdge(s, t, d)        │ 行为与重构前完全一致，向后兼容
├── nextVertexId()            ─┘
├── getVertex/getEdge/...      ← 非事务读，lock-free，看最新已提交快照
└── findVerticesByProperty/... ← 同上；故意不在 GraphTxn 上暴露

GraphTxn  (AutoCloseable)
├── commit() / rollback()
├── close()                              ← 未 commit/rollback → 隐式 rollback + WARN
├── addVertex / removeVertex / addEdge / removeEdge / nextVertexId
├── getVertex(t, id) / getEdge(s, t, d) ← getForUpdate，加写锁，read-your-own-writes
└── (NO find*Property*)                  ← 见 §4 决策
```

`GraphStore` 的同名 mutator 在重构后变成 3 行 wrapper：

```java
public void addVertex(Vertex v) throws RocksDBException {
    try (GraphTxn t = beginTransaction()) { t.addVertex(v); t.commit(); }
}
```

调用方原有代码**一行不用改**，新需要 read-modify-write 的代码升级到 `GraphTxn`。

## 3. 典型用法

```java
try (GraphStore store = new GraphStore("/var/data/graph")) {

    // 1) 一次性单步：和原来完全一样
    store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));

    // 2) 显式事务：read-modify-write 全程原子
    try (GraphTxn t = store.beginTransaction()) {
        Vertex v = t.getVertex(PERSON_TYPE, 1L);           // 在事务内加写锁
        if (v != null) {
            Vertex merged = v.withProperty("marker", true);
            t.addVertex(merged);                            // 不会被并发覆盖
        }
        t.commit();                                         // 必须显式 commit
    }

    // 3) 多顶点 + 多边的批操作：要么都成功要么都不存在
    try (GraphTxn t = store.beginTransaction()) {
        long aliceId = t.nextVertexId();
        long bobId   = t.nextVertexId();
        t.addVertex(new Vertex(aliceId, PERSON_TYPE).withProperty("name", "Alice"));
        t.addVertex(new Vertex(bobId,   PERSON_TYPE).withProperty("name", "Bob"));
        t.addEdge(new Edge(aliceId, bobId, KNOWS_TYPE).withProperty("since", "2020"));
        t.commit();
    }

    // 4) 查询仍然走 store（lock-free）
    List<Vertex> alices = store.findVerticesByProperty(PERSON_TYPE, "name", "Alice");
}
```

## 4. 关键决策与理由

### 4.1 为什么 `find*` 不放进 GraphTxn

`findVerticesByProperty / findEdgesByProperty` 内部是对 `cf_index / cf_edge_index` 的前缀迭代，命中数可能成百上千。如果把它放进事务：

- 要么用 `txn.getIterator + getForUpdate(每一行)`：锁粒度爆炸，整段索引区间被独占；
- 要么用 `txn.getIterator` 但不锁：和 `db.newIterator` 没差别，没必要绕事务。

权衡后：**保留 `find*` 在 `GraphStore` 上，走非事务读**。事务里如果真的需要"按属性筛"，调用方可以先 `find*` 拿出候选 id 列表，再在事务内逐个 `t.getVertex(id)` 加锁（按需锁定，粒度可控）。

### 4.2 为什么 `getVertex / getEdge` 在事务里要 `getForUpdate`

```java
// GraphTxn.getVertex
byte[] value = txn.getForUpdate(readOptions, store.cfVertex(), key, /*exclusive=*/ true);
```

如果只 `txn.get` 而不加锁：

1. 线程 A 在事务里 `get` 顶点 v（无锁）；
2. 线程 B 在另一个事务里 `addVertex(v')` 提交；
3. 线程 A 基于过时的 v 调 `addVertex(v'')`——丢了 B 的更新。

`getForUpdate` 给 row 加上**事务级写锁**，B 的事务必须等 A 释放才能开始。这才是 GraphTxn 解决并发 marker 累加的根本机制。

代价：同一个 vertex 上的并发事务会**完全串行化**。不同 vertex 之间互不阻塞——RocksDB 的锁是 row-level。

### 4.3 为什么 `close()` 时未 commit/rollback 要隐式 rollback

```java
try (GraphTxn t = store.beginTransaction()) {
    t.addVertex(...);
    // 忘了 commit 或代码路径上有早返
}
// close() 走 fallback：rollback + WARN
```

候选方案对比：

| 选项 | 结果 |
|---|---|
| 隐式 commit（像 JDBC autoCommit） | 部分写入被提交，状态损坏 |
| 抛 IllegalStateException | try-with-resources 中触发 → 掩盖原异常 |
| **隐式 rollback + WARN**（选用） | 安全：数据回滚到事务前；日志提醒开发者 |

测试 `shouldImplicitlyRollbackOnCloseWithoutCommit` 验证这一行为。

### 4.4 commit/rollback 之后再调用 mutator 怎么办

抛 `IllegalStateException`，提示当前事务已是 `COMMITTED` 或 `ROLLED_BACK`。理由：早失败 > 数据损坏。测试 `shouldRejectOperationsAfterCommit / AfterRollback` 验证。

## 5. 可见性矩阵

| 操作 | 看到的快照 |
|---|---|
| `store.getVertex / find*` （事务外） | 最新已提交快照（lock-free） |
| `t.getVertex / t.getEdge`（事务内，写锁） | 自己事务内写入 + 最新已提交（read-your-own-writes） |
| 另一线程的 `store.find*` 在 t 未 commit 时 | **看不到** t 的 pending 写入 |
| 另一线程的 `t2.getVertex(同 key)` | **阻塞**，等 t 提交或回滚 |

测试 `shouldHideUncommittedWritesFromOutsideReaders` 与 `shouldExposePendingIndexEntryAfterCommit` 覆盖这两条边界。

## 6. 性能注意

- **行锁 contention**：热点 vertex/edge 上并发事务会排队。事务里只放真正需要原子的步骤；纯读尽量走 `store.getVertex` / `find*`。
- **事务跨度**：事务越长，锁持有时间越长，对其他写者的阻塞越严重。**不要在事务里做远程 IO、长计算或交互等待**。
- **范围锁定不存在**：`getForUpdate` 只锁单个 key，不锁 range。如果两个事务读"不重叠 key 集合 + 写不重叠 key 集合"，可以完全并行。

## 7. 与底层 Transaction 的关系

`GraphTxn` 1:1 持有一个 `org.rocksdb.Transaction`。它的 commit / rollback / close 语义就是把 RocksDB 文档里的语义抽到一个 graph-shaped API 之后：

| GraphTxn | RocksDB |
|---|---|
| `t.getVertex / t.getEdge` | `txn.getForUpdate(cf, key, exclusive=true)` + 反序列化 |
| `t.addVertex / t.addEdge / t.removeXxx` | `txn.put / txn.delete` + 索引维护 |
| `t.nextVertexId` | `txn.getForUpdate(counter)` + `txn.put(counter+1)` |
| `t.commit()` | `txn.commit()` |
| `t.rollback()` | `txn.rollback()` |
| `t.close()` | `txn.close()`（前置 fallback rollback） |

RocksDB 的锁、隔离、死锁检测等行为完全透传。

## 8. 已知遗留

| 项 | 备注 |
|---|---|
| GraphTxn 不暴露 `find*` | 见 §4.1；若极少数场景必需，可以在事务里手动 `txn.getIterator` 走原始 API |
| RocksDB 行锁超时默认 1000ms | 高 contention 下可能 `Status.TimedOut`；后续可在 `TransactionDBOptions.setTransactionLockTimeout` 暴露成 GraphStore 构造参数 |
| GraphTxn 非线程安全 | 一个事务对应一个线程；跨线程共享需自己加同步 |
| 死锁检测 | RocksDB 内置死锁检测，会在循环等待时抛 `Status.Busy` 之一；GraphStore 当前没做自动重试，调用方需 catch |

## 9. 测试矩阵

| 套件 | 用例数 | 关注 |
|---|---|---|
| `GraphStoreTest` | 33 | 全部保留，原行为兼容 |
| `GraphStoreBenchmarkTest` | 7 | 未回归 |
| `GraphTxnTest` | **10**（新增） | commit / rollback / 隐式 rollback / 状态校验 / read-your-own-writes / 可见性 / 并发 marker 累加 / 批回滚 / 索引可见性 |

```
mvn -pl zora-rocksdb test  →  Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
mvn validate               →  Reactor 24 modules SUCCESS
```

## 10. 文件清单

```
zora-rocksdb/src/main/java/.../graph/store/
├── GraphStore.java   * 重构：mutator 全部变成 thin wrapper；
│                       新增 beginTransaction()；
│                       新增 package-private cfXxx() / propIdCache() 访问器；
│                       字典/edge-index helper 全部从这里移走到 GraphTxn
└── GraphTxn.java     + 全新：单文件 ~350 行，承载所有事务内写逻辑

zora-rocksdb/src/test/java/.../graph/store/
└── GraphTxnTest.java + 全新：10 个用例
```
