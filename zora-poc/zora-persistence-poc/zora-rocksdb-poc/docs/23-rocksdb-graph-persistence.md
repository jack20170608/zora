# 23 基于 RocksDB 的图数据持久化方案

## 目标

在已掌握的 RocksDB 基础能力之上，构建一个完整的**属性图（Property Graph）持久化 POC**，验证 RocksDB 作为图数据库底层存储的可行性。

---

## 1. 核心设计思想

图数据天然是高维关系数据，而 RocksDB 是二维 KV 存储。核心挑战在于**如何将图的结构与属性高效映射到有序的字节键空间**。

本方案采用以下设计原则：

| 原则 | 说明 |
|------|------|
| 前缀编码 | 通过 Key 前缀区分数据类型（Vertex、Edge、Property、Index） |
| 列族隔离 | 不同数据类型放入不同 ColumnFamily，便于独立调优和清理 |
| 双向边存储 | 同时存储出边和入边，空间换时间，实现 O(1) 邻居遍历 |
| 定长 Key | ID 使用 8 字节定长 long，保证字节序 = 数值序，支持高效 Range Scan |
| 原子写入 | 使用 WriteBatch 保证边双向记录的一致性 |

---

## 2. Key 编码设计

### 2.1 列族划分

```
cf_default  —— 元数据（序列号计数器、schema）
cf_vertex   —— 顶点主数据
cf_edge     —— 边数据（出边 + 入边混合存储）
cf_index    —— 属性二级索引
```

### 2.2 Key 格式

| 数据类型 | 格式 | 长度 |
|----------|------|------|
| Vertex | `V(1) \| typeId(4) \| vertexId(8)` | 13 bytes |
| Out Edge | `E(1) \| srcId(8) \| edgeType(4) \| 'O'(1) \| dstId(8)` | 22 bytes |
| In Edge | `E(1) \| dstId(8) \| edgeType(4) \| 'I'(1) \| srcId(8)` | 22 bytes |
| Index | `I(1) \| typeId(4) \| propHash(4) \| valueBytes \| vertexId(8)` | 变长 |

**为何双向存储边？**

```
出边 Key: E | src | type | O | dst   → 按 src 聚集，支持 "某节点的所有出边"
入边 Key: E | dst | type | I | src   → 按 dst 聚集，支持 "某节点的所有入边"
```

利用 RocksDB 的有序性，通过 `seek(prefix)` 即可定位到某一节点的所有边，无需全表扫描。

---

## 3. Value 设计

采用 **Jackson JSON** 序列化（复用已有依赖），便于 POC 阶段调试：

```json
// Vertex Value
{
  "id": 1001,
  "typeId": 1,
  "properties": {
    "name": "Alice",
    "age": 30
  }
}

// Edge Value
{
  "srcId": 1001,
  "dstId": 1002,
  "typeId": 2,
  "properties": {
    "since": "2020-01-01"
  }
}
```

> 生产环境可替换为 Protobuf / FlatBuffers 以获得更紧凑的存储。

---

## 4. 存储引擎架构

```
┌─────────────────────────────────────┐
│         GraphStore API              │
│  addVertex / addEdge / getNeighbors │
├─────────────────────────────────────┤
│         KeyCodec / ValueCodec       │
│  模型 ↔ 二进制 Key / JSON Value      │
├─────────────────────────────────────┤
│         RocksDB API                 │
│  WriteBatch / Iterator / Snapshot   │
├─────────────────────────────────────┤
│    cf_vertex  cf_edge  cf_index     │
└─────────────────────────────────────┘
```

### 4.1 核心 API

```java
// 顶点操作
void addVertex(Vertex vertex);
Vertex getVertex(long vertexId);
void removeVertex(long vertexId);

// 边操作（原子写入双向边）
void addEdge(Edge edge);
Edge getEdge(long srcId, int edgeType, long dstId);
void removeEdge(long srcId, int edgeType, long dstId);

// 遍历操作
List<Edge> getOutEdges(long vertexId, int edgeType);
List<Edge> getInEdges(long vertexId, int edgeType);
List<Vertex> getNeighbors(long vertexId, int edgeType);

// 属性索引查询
List<Vertex> findVerticesByProperty(int typeId, String propName, Object value);
```

### 4.2 原子性保证

边的写入使用 `WriteBatch` 同时提交出边和入边：

```java
WriteBatch batch = new WriteBatch();
batch.put(cfEdge, outKey, edgeValue);   // 出边
batch.put(cfEdge, inKey, edgeValue);    // 入边
batch.put(cfIndex, indexKey, EMPTY);    // 索引
db.write(writeOptions, batch);           // 原子提交
```

### 4.3 邻居遍历实现

```java
// 获取某节点的所有出边
byte[] prefix = KeyCodec.encodeEdgePrefix(vertexId, edgeType, Direction.OUT);
try (RocksIterator it = db.newIterator(cfEdge)) {
    for (it.seek(prefix); it.isValid() && startsWith(it.key(), prefix); it.next()) {
        Edge edge = ValueCodec.decodeEdge(it.value());
        // process edge
    }
}
```

利用 RocksDB 的 `Prefix Seek`，时间复杂度为 **O(k)**，其中 k 为该节点的边数。

---

## 5. 索引设计

### 5.1 二级索引结构

```
Index Key: I | typeId(4) | propHash(4) | valueBytes | vertexId(8)
```

- `propHash`: 属性名的 4 字节哈希，支持快速定位到某一属性的索引区间
- `valueBytes`: 属性值的字节表示（按类型编码），保证字典序以支持范围查询
- `vertexId`: 放在 Key 尾部，确保同一属性值下的不同顶点有序排列

### 5.2 索引维护

- **写入顶点**：同步计算索引 Key，写入 `cf_index`
- **更新属性**：先删除旧索引，再写入新索引（WriteBatch 原子操作）
- **删除顶点**：同步清理该顶点的所有索引 Entry

---

## 6. 序列化策略对比

| 场景 | 当前方案 | 生产优化建议 |
|------|----------|-------------|
| Key 编码 | ByteBuffer 自定义二进制 | 保持，已足够紧凑 |
| Value 编码 | Jackson JSON | 可替换为 Protobuf / FlatBuffers |
| 属性存储 | 内联到 Vertex/Edge JSON | 大属性可独立存储到 cf_property |

---

## 7. 验证检查清单

- [ ] 顶点 CRUD 操作正常
- [ ] 边双向存储，出边/入边遍历结果一致
- [ ] 边删除后双向记录均被清理
- [ ] WriteBatch 原子写入，异常时数据一致
- [ ] 属性索引支持等值查询
- [ ] 前缀扫描遍历的时间复杂度为 O(k)

---

## 8. 扩展方向

| 特性 | 实现思路 |
|------|----------|
| 图分区 | 对 Vertex ID 取模分区，跨分区边存储 "远程引用" |
| 时间旅行 | Key/Value 中嵌入时间戳，维护多版本数据 |
| 增量备份 | 利用 RocksDB Checkpoint + WAL |
| 全文检索 | 属性值分词后维护倒排索引，或对接 Elasticsearch |
| 标签传播 | 基于出边遍历实现 BFS/DFS 图算法 |
