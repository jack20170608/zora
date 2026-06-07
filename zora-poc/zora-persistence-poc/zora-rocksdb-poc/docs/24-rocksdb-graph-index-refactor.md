# 24 - GraphStore 索引重构：从 PoC 到生产级

> 状态：已落地（替换原 PoC 实现）
> 关联代码：`src/test/java/.../graph/codec/KeyCodec.java`、`graph/model/Property.java`、`graph/store/GraphStore.java`
> 关联测试：`GraphStoreTest`（21 个用例全部通过）

## 1. 重构动机

原 PoC 版 `GraphStore` 的索引存在以下生产化阻塞问题：

| # | 旧实现 | 问题 |
|---|---|---|
| 1 | `propHash = name.hashCode()` 4 字节 | 不同属性名可能哈希冲突 |
| 2 | value 用变长原始字节，字符串带 4 字节长度前缀 | `"Alic"` 会作为 `"Alice"` 的前缀混入，且无法做范围查询 |
| 3 | `addVertex` 直接覆盖写主数据，旧索引项不清理 | 改名/改值后，旧值索引泄漏，查得到幽灵 |
| 4 | `findVerticesByProperty` 命中后用 `getVertex(long)` 回表 | 回表不带 `typeId`，触发全表扫的 fallback |
| 5 | `WriteBatch` 未 close | JNI 内存泄漏 |
| 6 | `getVertex(long)` 全表扫 | 单点查询 O(N) |

本次重构在保留"4 个 CF + Key 即索引"整体哲学的前提下，对索引层做了系统性升级，并向后兼容了原有的 `addVertex / getVertex / addEdge / getOutEdges / getInEdges / findVerticesByProperty` 全部 API。

## 2. 存储分层（新增 `cf_schema`）

| Column Family | 角色 | Key | Value |
|---|---|---|---|
| `default` | 元数据计数器 | `__vertex_counter__` / `__prop_id_counter__` | 8 字节 / 4 字节 BE |
| `cf_vertex` | 顶点主存储 | `V \| typeId(4) \| vertexId(8)` | Vertex JSON |
| `cf_edge` | 边主存储 + 双向邻接表 | `E \| firstId(8) \| edgeType(4) \| dir(1) \| secondId(8)` | Edge JSON |
| `cf_index` | 属性二级索引（**等值 + 范围**） | `I \| typeId(4) \| propId(4) \| encodedValue \| vertexId(8)` | 空 |
| `cf_schema` | 属性名 ↔ propId 字典 | `S\|N\|<propName>` 或 `S\|I\|<propId>` | 反向值 |

> 新增 `cf_schema` 让"propId"成为一个全 DB 范围内分配、稳定可逆的整数标识，彻底取代不可靠的 `hashCode`。

## 3. Key 设计变化

### 3.1 索引 Key 布局

```
旧:  I | typeId(4) | propHash(4) | value(变长，无界)  | vertexId(8)
新:  I | typeId(4) | propId(4)   | encodedValue(自描述)| vertexId(8)
```

`encodedValue` 由 `Property#encodeValue` 产出，结构 `[tag(1) | fixedValue]`：

| tag | 类型 | 编码 | 长度 |
|---|---|---|---|
| `0x01` | long/int | `value ^ 0x8000000000000000L` 后 8 字节 BE（符号位翻转） | 9 |
| `0x02` | double/float | IEEE 754 bits `^ ((bits>>63) \| 0x8000…)`（正翻最高位，负全翻） | 9 |
| `0x03` | boolean | `0x00 / 0x01` | 2 |
| `0x04` | string | UTF-8，把 `0x00` 转义为 `0x00 0xFF`，再以 `0x00 0x00` 终止 | 变长 |
| `0x00` | null | 仅 tag | 1 |

> **核心保证**：所有编码后的 byte 序列，按 RocksDB 的 unsigned lex order 比较，结果等价于值本身的自然序。这是范围查询能用 `seek + 单调扫描` 实现的根本。

### 3.2 范围查询的扫描区间

```
lowerBound  = I | typeId | propId | encode(low)  | 0x00 × 8
upperBound  = I | typeId | propId | encode(high) | 0xFF × 8

seek(lowerBound)
while it.valid() && unsignedCompare(it.key(), upperBound) <= 0:
    yield decode(it.key())
```

## 4. 索引读写新行为

### 4.1 写：`addVertex` 改为 diff upsert

```
old = getVertex(typeId, id)                       // 一次点查
for p in old.props:
    if old.props[p] != new.props[p]:
        batch.delete(cfIndex, encode(typeId, propId(p), old.props[p], id))
for p in new.props:
    if old.props[p] != new.props[p]:
        batch.put(cfIndex, encode(typeId, propId(p), new.props[p], id), empty)
batch.put(cfVertex, encode(typeId, id), serialize(new))
db.write(batch)
```

效果：
- 改值时旧索引项被 **同批删除**，不再泄漏
- 没变化的属性 **不再重复写**，减小写放大
- `WriteBatch` 全部置于 try-with-resources，JNI 句柄不再泄漏

### 4.2 读：等值查询提前终止

由于 `encodedValue` 自身定长（数字/bool）或自终止（字符串），`indexValuePrefix(typeId, propId, encodedValue)` 是**完整的 value 前缀**，扫描首个不匹配的 key 即可 `break`，不再扫满整个 `(typeId, propId)` 段。

### 4.3 读：范围查询

新增 API：

```java
List<Vertex> findVerticesByPropertyRange(int typeId, String prop, Object low, Object high);
```

支持 `Integer / Long / Double / Float / Boolean / String`，两端均**闭区间**。

### 4.4 回表带 typeId

索引 Key 里本来就有 `typeId`，扫到一条索引项时直接拼出精确的 vertex key 做 `db.get(cfVertex, key)`，不再触发"扫整个 cf_vertex 找 id"的退化路径。

## 5. 字典管理（cf_schema）

- 首次访问某属性名时分配 propId（`__prop_id_counter__` 自增 4 字节），写入 `cf_schema` 双向条目，并加进进程内 `ConcurrentHashMap` 缓存。
- 重新打开 DB 时由 `warmPropIdCache()` 扫描 `S|N|*` 前缀整段加载，保证 propId 跨重启稳定。
- 查询接口 `findVerticesByProperty(*)` 在缓存中找不到属性名时**直接返回空**，避免无效的索引扫描。

## 6. 验证

新 `GraphStoreTest` 在原 14 个用例基础上新增 7 个针对性测试，全部绿：

| 新增用例 | 目的 |
|---|---|
| `shouldCleanUpStaleIndexEntriesOnUpdate` | 验证 diff upsert 删除旧值索引 |
| `shouldNotMatchByStringPrefix` | 验证字符串自终止编码防误匹配 |
| `shouldFindVerticesByIntegerRange` | 整数闭区间查询 |
| `shouldHandleNegativeNumbersInRange` | 负数排序正确（符号位翻转） |
| `shouldFindVerticesByDoubleRange` | 浮点（含负）排序正确 |
| `shouldFindVerticesByStringRange` | 字符串字典序范围 |
| `shouldPersistPropertyDictionaryAcrossReopens` | propId 字典跨重启可用 |

测试结果：`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`；
`GraphStoreBenchmarkTest` 7 个性能基线也仍然全部通过，证明 API 向后兼容。

## 7. 已知遗留

| # | 说明 |
|---|---|
| A | propId 计数器在 `propIdCache` 单进程锁下分配，**单进程**安全；多进程并发写入需替换为 `OptimisticTransactionDB` 或 `TransactionDB`。 |
| B | `addVertex` 是"先读后写"的事务，单个 `RocksDB` 写入下两步之间存在窗口；并发改同一顶点时需要乐观事务保护。 |
| C | 浮点 `NaN` 暂未单独处理，依赖 `Double.doubleToLongBits` 的标准化行为（所有 NaN 归一），不会破坏排序但会进入正无穷之后；如需精确排除可在 `encodeDouble` 入口判 `Double.isNaN`。 |
| D | `getVertex(long)` 仍保留全表扫 fallback，主要用于"只知道边端点 id 时的邻居取回"路径；后续若把 `vertexId -> typeId` 反查表实例化，可彻底干掉这条慢路径。 |

后续可在新文档中迭代解决以上四点。

## 8. 文件清单

```
src/test/java/top/ilovemyhome/zora/poc/persistence/rocksdb/graph/
├── codec/
│   ├── KeyCodec.java        # 重写：propId、indexValuePrefix、indexRangeLowerBound/UpperBound、compareUnsigned
│   └── ValueCodec.java      # 不变
├── model/
│   ├── Edge.java            # 不变
│   ├── Property.java        # 重写：tag + 保序编码（含字符串转义+终止符）
│   └── Vertex.java          # 不变
└── store/
    ├── GraphStore.java      # 重写：5 CF、字典、diff upsert、范围查询、资源管理
    └── GraphStoreTest.java  # 新增 7 个用例
```
