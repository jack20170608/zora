# zora-rocksdb-poc

RocksDB 功能与特性预研子模块。

## 目的

本模块通过测试用例形式，对 RocksDB 的 Java API（rocksdbjni）进行功能和特性预研，所有代码均在 `test` scope 下运行。

## RocksDB 简介

RocksDB 是由 Facebook 基于 Google LevelDB 开发的嵌入式、持久化的 Key-Value 存储引擎，采用 LSM-Tree（Log-Structured Merge-Tree）数据结构。它针对高写吞吐量和低延迟读取场景进行了深度优化，广泛应用于分布式系统、流处理引擎、消息队列、缓存等场景。

### 核心架构

```
┌─────────────────────────────────────────┐
│              MemTable (内存)              │  ← 跳表(SkipList)，有序写入
│  (Active → Immutable → Flush)            │
├─────────────────────────────────────────┤
│           WAL (Write Ahead Log)           │  ← 崩溃恢复，顺序写入
├─────────────────────────────────────────┤
│  Level-0 SST (可能有重叠 key 范围)         │
│  Level-1 SST (不重叠，Size 10MB)          │
│  Level-2 SST (不重叠，Size 100MB)         │  ← 多层 Compaction
│  ...                                     │
│  Level-N SST                             │
└─────────────────────────────────────────┘
```

## RocksDB 核心功能特性

### 1. 高性能写路径

| 特性 | 说明 |
|------|------|
| **LSM-Tree 结构** | 所有写操作先追加到 WAL 和内存中的 MemTable，避免随机磁盘 I/O |
| **WriteBatch 原子写入** | 将多条操作打包为单个 WAL 记录，保证原子性且减少 fsync 次数 |
| **异步/同步 WAL 模式** | `sync=false` 追求性能，`sync=true` 保证持久性，`disableWAL` 用于纯缓存场景 |
| **多线程 Compaction** | 后台线程自动将 MemTable flush 为 SST 文件，并跨层合并 |

### 2. 灵活的存储组织

| 特性 | 说明 |
|------|------|
| **Column Families** | 独立的键空间，每个 CF 有独立的 MemTable 和 SST，共享同一个 WAL |
| **多 Compaction 策略** | Leveled（默认，读优化）、Universal（写优化）、FIFO（类缓存淘汰） |
| **自定义 Comparator** | 支持自定义 key 排序逻辑（如逆序、复合 key 排序） |
| **Merge Operator** | 支持延迟合并的增量更新，避免 read-modify-write 开销 |

### 3. 高效的读取优化

| 特性 | 说明 |
|------|------|
| **Block Cache** | LRU 缓存 SST 文件中的 Block，减少重复磁盘 I/O |
| **Bloom Filter** | 快速判断 key 是否存在于 SST 中，显著降低不存在 key 的读放大 |
| **Snapshot 快照读** | 基于 sequence number 的一致性视图，不受并发写入影响 |
| **Iterator 范围扫描** | 支持正向/反向迭代、seek 定位、前缀扫描、范围扫描 |

### 4. 事务与一致性

| 特性 | 说明 |
|------|------|
| **悲观事务 (TransactionDB)** | 行级锁机制，冲突频繁场景下减少重试开销 |
| **乐观事务 (OptimisticTransactionDB)** | 无锁写入，冲突在 commit 时检测，适合低冲突场景 |
| **MVCC 多版本控制** | Snapshot 提供一致性读，支持时间旅行查询 |

### 5. 运维与扩展

| 特性 | 说明 |
|------|------|
| **Checkpoint** | 基于硬链接的轻量级快照，创建速度极快 |
| **BackupEngine** | 增量备份、多版本管理、跨实例恢复 |
| **详细统计信息** | Ticker 计数器、Histogram 延迟分布、Level 文件统计 |
| **动态配置调整** | 运行时可调整 compaction 线程数、缓存大小等参数 |

## 与竞品对比

### RocksDB vs LevelDB

| 维度 | RocksDB | LevelDB |
|------|---------|---------|
| **出品方** | Facebook (Meta) | Google |
| **多线程** | 全面支持（多线程 flush/compaction） | 单线程 compaction |
| **Column Families** | 支持 | 不支持 |
| **事务** | 悲观 + 乐观事务 | 不支持 |
| **备份工具** | BackupEngine + Checkpoint | 无内置备份 |
| **Bloom Filter** | 每个 SST 文件独立配置 | 简单支持 |
| **Compaction 策略** | Leveled / Universal / FIFO | 仅 Leveled |
| **适用场景** | 生产级高并发场景 | 轻量级嵌入式场景 |
| **社区活跃度** | 极高，持续迭代 | 维护模式 |

> LevelDB 是 RocksDB 的前身，RocksDB 在 LevelDB 基础上增加了多线程、事务、CF、丰富调优参数等能力，已成为生产环境的主流选择。

### RocksDB vs LMDB

| 维度 | RocksDB | LMDB |
|------|---------|------|
| **数据结构** | LSM-Tree | B+Tree (内存映射) |
| **写模型** | 顺序写（追加日志） | 写时复制 (COW) |
| **读性能** | 依赖 Block Cache 和 Bloom Filter | 极快（直接内存映射访问） |
| **写性能** | 极高（纯内存写 + 后台 flush） | 一般（COW 导致写放大） |
| **事务** | 支持 | 内置 MVCC，只读事务无锁 |
| **内存占用** | 可配置（MemTable + BlockCache） | 与数据库大小成正比（mmap） |
| **最大数据量** | TB 级别 | 通常几百 GB（受地址空间限制） |
| **适用场景** | 写密集型、大数据量 | 读密集型、中小数据量 |

> LMDB 使用纯内存映射，读取延迟极低，但写入受 COW 限制。RocksDB 更适合写多读少、数据量大的场景。

### RocksDB vs SQLite

| 维度 | RocksDB | SQLite |
|------|---------|--------|
| **类型** | Key-Value 存储引擎 | 完整的关系型数据库 |
| **SQL 支持** | 不支持（需上层封装） | 完整支持 |
| **索引** | 仅主键索引 | 多索引、复合索引、全文索引 |
| **事务隔离** | Snapshot Isolation | SERIALIZABLE / READ COMMITTED |
| **数据模型** | 扁平 KV | 结构化表、外键、触发器 |
| **查询能力** | 仅 key 精确查找或前缀扫描 | 复杂 JOIN、聚合、子查询 |
| **适用场景** | 底层存储引擎、缓存、时序数据 | 应用内数据库、离线分析、配置存储 |

> SQLite 是完整的关系型数据库，适合需要 SQL 查询能力的场景。RocksDB 作为底层 KV 引擎，通常被 MyRocks、TiKV、Flink State Backend 等系统用作存储层。

### RocksDB vs BadgerDB

| 维度 | RocksDB | BadgerDB |
|------|---------|----------|
| **语言** | C++（JNI 绑定） | Go |
| **出品方** | Facebook | Dgraph |
| **LSM-Tree 变体** | 标准 LSM | WiscKey（key-value 分离） |
| **value 存储** | 与 key 同 SST 文件 | value 存日志文件，SST 只存 key + 指针 |
| **大 value 性能** | 一般（compaction 开销大） | 优秀（value 不参与 compaction） |
| **GC 机制** | Compaction 自然回收 | 独立垃圾回收 |
| **生态绑定** | Java/C++/Python 成熟 | Go 生态首选 |
| **适用场景** | 通用 KV、小value为主 | Go 生态、大value、图数据库 |

> BadgerDB 的 WiscKey 设计在 value 较大时（>1KB）有明显优势，但 RocksDB 生态更成熟，社区支持和调优文档更丰富。

### 综合对比表

| 特性 | RocksDB | LevelDB | LMDB | SQLite | BadgerDB |
|------|---------|---------|------|--------|----------|
| 语言 | C++ | C++ | C | C | Go |
| 数据结构 | LSM-Tree | LSM-Tree | B+Tree (mmap) | B-Tree | LSM-Tree (WiscKey) |
| 写吞吐 | 极高 | 高 | 中 | 中 | 极高 |
| 读吞吐 | 高 | 中 | 极高 | 高 | 高 |
| 大 Value 优化 | 一般 | 一般 | 一般 | 一般 | 优秀 |
| 事务支持 | 悲观+乐观 | 无 | MVCC | 完整 ACID | MVCC |
| Column Family | 支持 | 不支持 | 不支持 | 表概念 | 不支持 |
| 备份机制 | Checkpoint + BackupEngine | 文件复制 | 文件复制 | `.backup` | 不支持 |
| 生产成熟度 | 极高 | 中 | 高 | 极高 | 中 |
| 典型用户 | MyRocks, TiKV, Flink | Chrome IndexedDB | OpenLDAP | Android, iOS | Dgraph |

## 选型建议

| 场景 | 推荐选择 |
|------|----------|
| 需要作为分布式系统存储层（高写入、TB 级数据） | **RocksDB** |
| 需要完整 SQL 能力、应用内嵌入式数据库 | SQLite |
| 读多写少、追求最低读取延迟、数据量 < 100GB | LMDB |
| Go 生态、大 value 场景（>1KB）、图数据库 | BadgerDB |
| 简单嵌入式 KV、学习目的、轻量级应用 | LevelDB |
| 需要悲观/乐观事务、复杂配置调优 | **RocksDB** |
| 流处理状态存储、消息队列持久化 | **RocksDB** |

## 运行测试

```bash
mvn test -pl zora-rocksdb-poc
```

或从项目根目录：

```bash
mvn test -pl zora-poc/zora-persistence-poc/zora-rocksdb-poc
```
