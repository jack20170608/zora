# 18 SST 文件与 Compaction 调优

## 目标

理解 LSM-Tree 结构，掌握不同 Compaction 策略的配置与影响，观察 compaction 行为。

---

## 步骤 1：理解 LSM-Tree 结构

RocksDB 使用 Log-Structured Merge-Tree（LSM-Tree）存储数据：

```
MemTable (内存)
    |
Immutable MemTable
    |
Level-0 SST files (可能有重叠的 key 范围)
    |
Level-1 SST files (不重叠)
    |
Level-2 SST files (更大，不重叠)
    |
...
Level-N
```

Compaction 将低层（较新）的 SST 文件合并到高层（较旧），消除重复和删除标记。

---

## 步骤 2：Compaction 策略配置

### 2.1 三种策略对比

| 策略 | 类 | 特点 |
|---|---|---|
| Leveled | `CompactionStyle.LEVEL` (默认) | 每层大小固定倍数增长，空间放大最小，读性能最好 |
| Universal | `CompactionStyle.UNIVERSAL` | 类似 Size-Tiered，写放大最小，适合写密集型 |
| FIFO | `CompactionStyle.FIFO` | 只保留最近的数据，超出大小后删除旧文件，类似缓存 |

### 2.2 配置 Universal Compaction

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompactionTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUseUniversalCompaction() throws RocksDBException {
        RocksDB.loadLibrary();

        Options options = new Options()
            .setCreateIfMissing(true)
            .setCompactionStyle(CompactionStyle.UNIVERSAL)
            .setLevel0FileNumCompactionTrigger(2)
            .setMaxBytesForLevelBase(64 * 1024 * 1024);

        RocksDB db = RocksDB.open(options, tempDir.toString());

        // Write data to trigger compaction
        byte[] value = new byte[1024 * 1024];  // 1MB
        for (int i = 0; i < 100; i++) {
            db.put(("key" + i).getBytes(), value);
        }

        // Force flush to create L0 files
        db.flush(new FlushOptions().setWaitForFlush(true));

        // Check compaction stats
        String stats = db.getProperty("rocksdb.stats");
        System.out.println(stats);

        db.close();
        options.close();
    }
}
```

---

## 步骤 3：观察 Compaction 行为

### 3.1 查看 Level 文件分布

```java
@Test
void shouldObserveLevelFiles() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options()
        .setCreateIfMissing(true)
        .setWriteBufferSize(1 * 1024 * 1024)          // 1MB memtable
        .setMaxWriteBufferNumber(2)
        .setLevel0FileNumCompactionTrigger(2)
        .setTargetFileSizeBase(1 * 1024 * 1024)       // 1MB per SST
        .setMaxBytesForLevelBase(10 * 1024 * 1024);   // 10MB for L1

    RocksDB db = RocksDB.open(options, tempDir.toString());

    // Write enough data to trigger multiple compactions
    byte[] value = new byte[100 * 1024];  // 100KB
    for (int i = 0; i < 500; i++) {
        db.put(("k" + String.format("%04d", i)).getBytes(), value);
    }

    // Wait for background compaction
    try {
        Thread.sleep(2000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    // Check file distribution across levels
    for (int level = 0; level <= 6; level++) {
        String prop = "rocksdb.num-files-at-level" + level;
        String count = db.getProperty(prop);
        System.out.println("Level " + level + ": " + count + " files");
    }

    db.close();
    options.close();
}
```

---

## 步骤 4：动态 Level 调整

### 4.1 启用动态 Level 大小

```java
@Test
void shouldUseDynamicLevelBytes() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options()
        .setCreateIfMissing(true)
        .setLevelCompactionDynamicLevelBytes(true)
        .setMaxBytesForLevelBase(64 * 1024 * 1024)
        .setMaxBytesForLevelMultiplier(10);

    RocksDB db = RocksDB.open(options, tempDir.toString());

    byte[] value = new byte[1024 * 1024];
    for (int i = 0; i < 200; i++) {
        db.put(("key" + i).getBytes(), value);
    }

    db.flush(new FlushOptions().setWaitForFlush(true));

    // With dynamic levels, the base level may shift based on data size
    String baseLevel = db.getProperty("rocksdb.base-level");
    System.out.println("Base level: " + baseLevel);

    db.close();
    options.close();
}
```

### 4.2 动态 Level 的作用

当 `level_compaction_dynamic_level_bytes=true` 时，RocksDB 会根据实际数据量动态调整 L1 的大小，避免小数据量时产生过多空 level。

---

## 步骤 5：Compaction 统计解读

### 5.1 读取 Compaction 统计

```java
@Test
void shouldReadCompactionStats() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options()
        .setCreateIfMissing(true)
        .setStatistics(new Statistics());

    RocksDB db = RocksDB.open(options, tempDir.toString());

    byte[] value = new byte[1024 * 1024];
    for (int i = 0; i < 50; i++) {
        db.put(("key" + i).getBytes(), value);
    }

    db.flush(new FlushOptions().setWaitForFlush(true));

    // Wait for compaction
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    // Print detailed stats
    String stats = db.getProperty("rocksdb.stats");
    System.out.println("=== Compaction Stats ===");
    System.out.println(stats);

    db.close();
    options.close();
}
```

### 5.2 关键指标解读

```
Level    Files   Size(MB)  Score Read(GB)  Rn(GB) Rnp1(GB) ...
----------------------------------------------------------------
  L0      2       2.0      2.0     0.0      0.0     0.0
  L1      1      10.0      1.0     0.5      0.2     0.3
  L2      5      50.0      0.8     1.2      0.5     0.7
```

| 列 | 含义 |
|---|---|
| Files | 该层 SST 文件数量 |
| Size | 该层总数据大小 |
| Score | Compaction 优先级分数，>1 表示需要 compaction |
| Read | Compaction 时读取的数据量 |
| Rn | 从当前层读取的数据量 |
| Rnp1 | 从下一层读取的数据量 |

---

## 验证检查清单

- [ ] `CompactionStyle.LEVEL` 下 L1 文件不重叠
- [ ] `CompactionStyle.UNIVERSAL` 下写放大更小
- [ ] `CompactionStyle.FIFO` 下旧数据自动删除
- [ ] 数据量增大后高层（L2, L3...）出现文件
- [ ] `level_compaction_dynamic_level_bytes=true` 后 base level 动态调整
- [ ] `rocksdb.stats` 可输出各层文件分布和 compaction 历史

---

## 常见问题

**Q: Compaction 太频繁怎么办？**
A: 增大 `write_buffer_size` 和 `target_file_size_base`，或调大 `level0_file_num_compaction_trigger`。

**Q: Compaction 太少导致读性能下降？**
A: 减小 `level0_file_num_compaction_trigger`，或增加后台线程 `max_background_jobs`。

**Q: 空间放大如何计算？**
A: 空间放大 = 磁盘总 SST 大小 / 实际有效数据大小。Leveled 约 1.1-1.2x，Universal 约 2-3x，FIFO 取决于保留策略。
