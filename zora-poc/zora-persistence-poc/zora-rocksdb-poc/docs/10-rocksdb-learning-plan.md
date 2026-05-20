# RocksDB 学习计划

## 概述

本计划面向 RocksDB Java API（rocksdbjni）进行系统性学习，所有实践代码均在 `zora-rocksdb-poc` 模块中以测试用例形式编写。

---

## 阶段一：基础入门（Week 1）

### 1.1 环境搭建与 Hello World
- **目标**：能够打开/关闭一个 RocksDB 实例，完成最简单的 Put / Get / Delete。
- **关键 API**：
  - `RocksDB.loadLibrary()`
  - `RocksDB.open(Options, String)`
  - `RocksDB.put(byte[], byte[])`
  - `RocksDB.get(byte[])`
  - `RocksDB.delete(byte[])`
  - `RocksDB.close()`
- **实践要点**：
  - 理解 `Options` 的作用（创建、配置、关闭）。
  - 使用 `@TempDir` 管理测试数据库目录，避免污染本地文件系统。
  - 正确处理 `RocksDBException`。

### 1.2 配置选项初探
- **目标**：了解最常用配置项的含义与默认值。
- **关键 API**：
  - `Options.setCreateIfMissing(true)`
  - `Options.setDbWriteBufferSize()`
  - `Options.setMaxOpenFiles()`
- **实践要点**：
  - 对比不同配置下写性能的差异（简单 benchmark）。

---

## 阶段二：核心 API 掌握（Week 2）

### 2.1 WriteBatch 批量写入
- **目标**：掌握批量原子写入，理解其与单条写入的性能差异。
- **关键 API**：
  - `WriteBatch`
  - `WriteBatch.put(byte[], byte[])`
  - `WriteBatch.delete(byte[])`
  - `RocksDB.write(WriteOptions, WriteBatch)`
- **实践要点**：
  - 对比 `WriteBatch` vs 循环 `put` 的性能。
  - 验证批量写入的原子性（中途异常时是否全部回滚）。

### 2.2 Column Families
- **目标**：理解 Column Family 的概念，掌握多 CF 的 CRUD。
- **关键 API**：
  - `ColumnFamilyDescriptor`
  - `ColumnFamilyHandle`
  - `RocksDB.open(DBOptions, String, List<ColumnFamilyDescriptor>, List<ColumnFamilyHandle>)`
  - `RocksDB.createColumnFamily(ColumnFamilyDescriptor)`
  - `RocksDB.dropColumnFamily(ColumnFamilyHandle)`
- **实践要点**：
  - 将不同业务数据隔离到不同 CF。
  - 注意 `ColumnFamilyHandle` 的关闭顺序。

---

## 阶段三：进阶特性（Week 3）

### 3.1 Iterator 与范围扫描
- **目标**：掌握正向/反向迭代、前缀扫描、范围扫描。
- **关键 API**：
  - `RocksDB.newIterator()`
  - `RocksIterator.seek(byte[])`
  - `RocksIterator.seekToFirst()` / `seekToLast()`
  - `RocksIterator.isValid()` / `next()` / `prev()`
- **实践要点**：
  - 理解迭代器必须在使用后 `close()`（try-with-resources）。
  - 实现前缀扫描（利用 key 设计 + `seek`）。

### 3.2 Snapshot 快照读
- **目标**：理解 Snapshot 的语义，掌握一致性读。
- **关键 API**：
  - `RocksDB.getSnapshot()`
  - `ReadOptions.setSnapshot(Snapshot)`
  - `Snapshot.close()`
- **实践要点**：
  - 在并发写入场景下，验证 Snapshot 读的一致性视图。

### 3.3 事务支持（TransactionDB）
- **目标**：了解 RocksDB 的乐观/悲观事务模型。
- **关键 API**：
  - `TransactionDB.open(Options, TransactionDBOptions, String)`
  - `TransactionDB.beginTransaction(WriteOptions)`
  - `Transaction.put()` / `Transaction.get()` / `Transaction.commit()` / `Transaction.rollback()`
- **实践要点**：
  - 对比乐观事务与悲观事务的冲突检测行为。

---

## 阶段四：性能调优（Week 4）

### 4.1 MemTable 与 WAL 调优
- **目标**：理解写路径，掌握 MemTable 和 WAL 相关配置。
- **关键配置**：
  - `Options.setWriteBufferSize()`（单个 MemTable 大小）
  - `Options.setMaxWriteBufferNumber()`（最大 MemTable 数量）
  - `Options.setMinWriteBufferNumberToMerge()`
  - `WriteOptions.setSync(boolean)` / `setDisableWAL(boolean)`
- **实践要点**：
  - 对比 `sync=true/false` 的写性能与持久性保证。

### 4.2 SST 文件与 Compaction 调优
- **目标**：理解 LSM-Tree 结构，掌握 Compaction 策略。
- **关键配置**：
  - `Options.setCompactionStyle(CompactionStyle)`
  - `Options.setTargetFileSizeBase()` / `setTargetFileSizeMultiplier()`
  - `Options.setLevelCompactionDynamicLevelBytes(true)`
  - `Options.setMaxBytesForLevelBase()`
- **实践要点**：
  - 观察不同 Compaction 策略（LEVEL / UNIVERSAL / FIFO）对空间放大和读放大的影响。

### 4.3 Block Cache 与布隆过滤器
- **目标**：掌握读缓存和过滤器的配置，减少不必要的磁盘 I/O。
- **关键 API**：
  - `Cache` / `LRUCache`
  - `BlockBasedTableConfig.setBlockCache(Cache)`
  - `BlockBasedTableConfig.setFilterPolicy(BloomFilter)`
- **实践要点**：
  - 对比开启/关闭 Block Cache 的读性能。
  - 对比开启/关闭 BloomFilter 对不存在 key 的查询性能。

---

## 阶段五：运维与高级特性（Week 5）

### 5.1 备份与恢复
- **目标**：掌握 RocksDB 的 Checkpoint 和 Backup 机制。
- **关键 API**：
  - `Checkpoint.create(RocksDB)`
  - `Checkpoint.createCheckpoint(String)`
  - `BackupEngine` / `BackupEngineOptions`
- **实践要点**：
  - 验证 Checkpoint 是硬链接还是副本（与底层文件系统相关）。
  - 从 Backup 恢复数据。

### 5.2 统计与监控
- **目标**：了解如何获取 RocksDB 的运行时统计信息。
- **关键 API**：
  - `Options.setStatistics(Statistics)`
  - `Statistics.getTickerCount(TickerType)`
  - `RocksDB.getProperty("rocksdb.stats")`
- **实践要点**：
  - 打印并解读 `rocksdb.stats` 输出。
  - 监控关键指标：block cache hit/miss、compaction 次数等。

### 5.3 自定义 Comparator / Merge Operator
- **目标**：了解高级扩展点。
- **关键 API**：
  - `AbstractComparator`
  - `AbstractMergeOperator`
- **实践要点**：
  - 实现一个简单的自定义 Comparator（如逆序比较）。
  - 实现一个 Merge Operator（如计数器累加）。

---

## 推荐学习资源

| 资源 | 说明 |
|---|---|
| [RocksDB Wiki](https://github.com/facebook/rocksdb/wiki) | 官方文档，概念和调优指南 |
| [RocksDB Java API Javadoc](https://javadoc.io/doc/org.rocksdb/rocksdbjni) | API 参考 |
| RocksDB `examples/` 目录 | 官方 C++ 示例，概念通用 |

---

## 进度检查清单

- [ ] 阶段一：环境搭建与 Hello World
- [ ] 阶段二：WriteBatch 与 Column Families
- [ ] 阶段三：Iterator、Snapshot、Transaction
- [ ] 阶段四：MemTable、Compaction、BlockCache 调优
- [ ] 阶段五：备份恢复、统计监控、高级扩展
