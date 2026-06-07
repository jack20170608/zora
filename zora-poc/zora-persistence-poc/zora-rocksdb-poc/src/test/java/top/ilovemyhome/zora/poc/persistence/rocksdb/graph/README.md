# GraphStore — 基于 RocksDB 的属性图存储引擎

## 概述

GraphStore 是一个构建于 RocksDB 之上的**属性图（Property Graph）**持久化引擎 POC。它将图的高维关系数据（顶点、边、属性）映射到 RocksDB 的二维 KV 存储中，支持：

- 顶点与边的 CRUD 操作
- 双向边存储（出边 / 入边），实现 O(k) 邻居遍历
- 属性二级索引，支持按属性值查询顶点
- 原子批量写入（WriteBatch），保证图结构一致性

## 快速开始

### 1. 打开图存储

```java
GraphStore store = new GraphStore("/path/to/db");
```

### 2. 创建顶点

```java
long aliceId = store.nextVertexId();
Vertex alice = new Vertex(aliceId, 1)  // typeId = 1 (Person)
    .withProperty("name", "Alice")
    .withProperty("age", 30);
store.addVertex(alice);
```

### 3. 创建边

```java
Edge edge = new Edge(aliceId, bobId, 10)  // typeId = 10 (KNOWS)
    .withProperty("since", "2020-01-01");
store.addEdge(edge);  // 原子写入出边 + 入边
```

### 4. 遍历邻居

```java
// 获取 Alice 的所有朋友（出边遍历）
List<Vertex> friends = store.getNeighbors(aliceId, 10);

// 获取关注 Bob 的人（入边遍历）
List<Edge> followers = store.getInEdges(bobId, 11);  // typeId = 11 (FOLLOWS)
```

### 5. 属性索引查询

```java
// 查找所有年龄为 30 的人
List<Vertex> result = store.findVerticesByProperty(1, "age", 30);
```

### 6. 关闭

```java
store.close();
```

## 架构设计

### 存储布局

GraphStore 使用 4 个 RocksDB ColumnFamily 隔离不同类型的数据：

| 列族 | 用途 | Key 格式 |
|------|------|---------|
| `default` | 元数据（序列号计数器） | — |
| `vertex` | 顶点数据 | `V \| typeId(4) \| vertexId(8)` |
| `edge` | 边数据（双向存储） | `E \| id(8) \| edgeType(4) \| dir(1) \| id(8)` |
| `index` | 属性二级索引 | `I \| typeId(4) \| propHash(4) \| value \| vertexId(8)` |

### 双向边存储

```
出边 Key: E | srcId(8) | edgeType(4) | 'O' | dstId(8)
入边 Key: E | dstId(8) | edgeType(4) | 'I' | srcId(8)
```

利用 RocksDB 的有序性，通过 `seek(prefix)` 即可定位某一节点的所有边，无需全表扫描。

### 序列化

- **Key**: 自定义二进制编码（`ByteBuffer`，大端序），定长且字典序 = 数值序
- **Value**: Jackson JSON（POC 阶段便于调试），生产环境可替换为 Protobuf

## 核心 API

### 顶点操作

```java
void addVertex(Vertex vertex)
Vertex getVertex(int typeId, long vertexId)
Vertex getVertex(long vertexId)        // 全表扫描（无 typeId 时）
void removeVertex(int typeId, long vertexId)  // 级联删除关联的边和索引
long nextVertexId()                    // 获取自增顶点 ID
```

### 边操作

```java
void addEdge(Edge edge)                // 原子写入出边 + 入边
Edge getEdge(long srcId, int edgeType, long dstId)
void removeEdge(long srcId, int edgeType, long dstId)  // 原子删除双向边
```

### 遍历操作

```java
List<Edge> getOutEdges(long vertexId, int edgeType)
List<Edge> getInEdges(long vertexId, int edgeType)
List<Vertex> getNeighbors(long vertexId, int edgeType)
```

### 索引查询

```java
List<Vertex> findVerticesByProperty(int typeId, String propName, Object value)
```

支持的数据类型：`String`、`Integer`、`Long`、`Double`、`Boolean`。

## 开发指南

### 添加新的顶点类型

无需注册，直接使用新的 `typeId` 即可：

```java
int COMPANY_TYPE = 2;
Vertex company = new Vertex(store.nextVertexId(), COMPANY_TYPE)
    .withProperty("name", "Acme Corp")
    .withProperty("founded", 1990);
store.addVertex(company);
```

### 添加新的边类型

同样无需注册，使用新的 `typeId`：

```java
int WORKS_AT = 20;
store.addEdge(new Edge(personId, companyId, WORKS_AT)
    .withProperty("role", "Engineer"));
```

### 事务与一致性

- 单条边的写入（出边 + 入边）使用 `WriteBatch` 原子提交
- 顶点的写入（主数据 + 索引）同样使用 `WriteBatch`
- 顶点删除会级联清理所有关联的边和索引（批量操作）

## 性能特征

| 操作 | 时间复杂度 | 说明 |
|------|-----------|------|
| 顶点写入 | O(p) | p = 属性数量（含索引维护） |
| 边写入 | O(1) | 固定写入 2 条 KV |
| 出边/入边遍历 | O(k) | k = 该节点的边数，前缀扫描 |
| 邻居查询 | O(k) + O(k×顶点读取) | 含顶点反序列化 |
| 属性索引查询 | O(m) | m = 匹配该属性值的顶点数 |
| 顶点删除 | O(k + p) | 清理所有边和索引 |

> 详细基准测试数据见 `GraphStoreBenchmarkTest`。

## 扩展方向

| 特性 | 实现思路 |
|------|---------|
| 图算法层 | 基于 `getNeighbors()` 实现 BFS/DFS/最短路径 |
| 批量导入 | 使用 `WriteBatch` 批量写入，关闭 WAL 提升吞吐量 |
| 分布式 | 按 `vertexId` 取模分区，跨分区边存储远程引用 |
| 时序版本 | Key/Value 中嵌入时间戳，支持历史版本查询 |

## 测试

运行全部图存储测试：

```bash
mvn test -pl zora-rocksdb-poc -Dtest=GraphStoreTest
```

运行性能基准测试：

```bash
mvn test -pl zora-rocksdb-poc -Dtest=GraphStoreBenchmarkTest
```

## 文件结构

```
graph/
├── model/
│   ├── Vertex.java          # 顶点领域模型
│   ├── Edge.java            # 边领域模型
│   └── Property.java        # 属性编码工具
├── codec/
│   ├── KeyCodec.java        # Key 二进制编解码
│   └── ValueCodec.java      # Value JSON 编解码
├── store/
│   ├── GraphStore.java      # 存储引擎核心
│   ├── GraphStoreTest.java  # 集成测试
│   └── GraphStoreBenchmarkTest.java  # 性能基准测试
└── README.md                # 本文档
```
