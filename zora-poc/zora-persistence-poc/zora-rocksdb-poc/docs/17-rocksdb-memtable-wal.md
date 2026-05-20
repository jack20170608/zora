# 17 MemTable 与 WAL 调优

## 目标

理解 RocksDB 写路径（Write Path），掌握 MemTable 和 WAL 相关配置，通过实验验证不同配置对性能和持久性的影响。

---

## 步骤 1：理解写路径

RocksDB 的写路径如下：

```
Client Put
    |
    v
Write Ahead Log (WAL) --> OS Page Cache (or fsync to disk)
    |
    v
MemTable (in-memory sorted structure)
    |
    v
Immutable MemTable --> Flush --> SST File (Level-0)
```

| 组件 | 作用 | 配置点 |
|---|---|---|
| WAL | 崩溃恢复，顺序写入磁盘 | `WriteOptions.setSync()` / `setDisableWAL()` |
| MemTable | 内存中的有序结构，跳表实现 | `ColumnFamilyOptions.setWriteBufferSize()` |
| Immutable MemTable | 待 flush 的只读 MemTable | `setMaxWriteBufferNumber()` |

---

## 步骤 2：WAL 配置实验

### 2.1 同步写 vs 异步写 vs 禁用 WAL

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemTableWalTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCompareWalModes() throws RocksDBException {
        RocksDB.loadLibrary();

        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        int count = 500;
        byte[] value = "x".repeat(100).getBytes();

        // Mode 1: sync=false (default)
        long t1 = System.currentTimeMillis();
        try (WriteOptions wo = new WriteOptions().setSync(false)) {
            for (int i = 0; i < count; i++) {
                db.put(wo, ("k" + i).getBytes(), value);
            }
        }
        long asyncMs = System.currentTimeMillis() - t1;

        // Mode 2: sync=true
        long t2 = System.currentTimeMillis();
        try (WriteOptions wo = new WriteOptions().setSync(true)) {
            for (int i = count; i < count * 2; i++) {
                db.put(wo, ("k" + i).getBytes(), value);
            }
        }
        long syncMs = System.currentTimeMillis() - t2;

        // Mode 3: disable WAL
        long t3 = System.currentTimeMillis();
        try (WriteOptions wo = new WriteOptions().setDisableWAL(true)) {
            for (int i = count * 2; i < count * 3; i++) {
                db.put(wo, ("k" + i).getBytes(), value);
            }
        }
        long noWalMs = System.currentTimeMillis() - t3;

        System.out.println("async (sync=false): " + asyncMs + " ms");
        System.out.println("sync (sync=true):   " + syncMs + " ms");
        System.out.println("no WAL:             " + noWalMs + " ms");

        // Performance expectation: noWAL <= async <= sync
        assertThat(noWalMs).isLessThanOrEqualTo(asyncMs + 100);  // allow some variance

        db.close();
        options.close();
    }
}
```

### 2.2 WAL 模式对比

| 模式 | 速度 | 崩溃安全性 | 适用场景 |
|---|---|---|---|
| `sync=false` | 快 | 可能丢失最后几秒数据 | 一般业务，可接受少量丢失 |
| `sync=true` | 慢 | 不丢失已确认写入 | 金融、关键数据 |
| `disableWAL=true` | 最快 | 进程崩溃即丢数据 | 缓存、可重建数据 |

---

## 步骤 3：MemTable 配置实验

### 3.1 调整 MemTable 大小

```java
@Test
void shouldConfigureMemTableSize() throws RocksDBException {
    RocksDB.loadLibrary();

    ColumnFamilyOptions cfOptions = new ColumnFamilyOptions()
        .setWriteBufferSize(4 * 1024 * 1024)       // 4MB per memtable
        .setMaxWriteBufferNumber(3)                 // max 3 memtables
        .setMinWriteBufferNumberToMerge(2);         // merge 2 before flush

    DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);

    List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
    cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));

    List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

    try (RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {
        // Write enough data to trigger flush
        byte[] value = new byte[1024 * 1024];  // 1MB
        for (int i = 0; i < 20; i++) {
            db.put(("key" + i).getBytes(), value);
        }

        // Check if flush happened
        String stats = db.getProperty("rocksdb.num-immutable-mem-table");
        System.out.println("Immutable memtables: " + stats);

        // Force flush to observe behavior
        db.flush(new FlushOptions().setWaitForFlush(true));

        String numFiles = db.getProperty("rocksdb.num-files-at-level0");
        System.out.println("Level-0 files: " + numFiles);
    }

    for (ColumnFamilyHandle handle : cfHandles) {
        handle.close();
    }
    cfOptions.close();
    dbOptions.close();
}
```

### 3.2 MemTable 配置速查

| 配置项 | 默认值 | 作用 |
|---|---|---|
| `setWriteBufferSize` | 64MB | 单个 MemTable 的内存上限，写满后转为 Immutable |
| `setMaxWriteBufferNumber` | 2 | 允许同时存在的 MemTable 总数（active + immutable） |
| `setMinWriteBufferNumberToMerge` | 1 | flush 前需要合并的 Immutable MemTable 数量 |

---

## 步骤 4：Flush 行为观察

### 4.1 手动触发 Flush

```java
@Test
void shouldTriggerFlushManually() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, tempDir.toString());

    db.put("key".getBytes(), "value".getBytes());

    // Before flush: check if data is only in memtable
    String immCount = db.getProperty("rocksdb.num-immutable-mem-table");
    System.out.println("Before flush - immutable memtables: " + immCount);

    // Trigger flush
    db.flush(new FlushOptions().setWaitForFlush(true));

    // After flush: data is in SST file
    String level0Files = db.getProperty("rocksdb.num-files-at-level0");
    System.out.println("After flush - level0 files: " + level0Files);

    assertThat(Integer.parseInt(level0Files)).isGreaterThanOrEqualTo(1);

    db.close();
    options.close();
}
```

---

## 验证检查清单

- [ ] `sync=false` 写入后正常关闭数据库，数据可恢复
- [ ] `sync=true` 写入性能低于 `sync=false`
- [ ] `disableWAL=true` 写入后强制杀进程，数据丢失
- [ ] 增大 `setWriteBufferSize` 可减少 flush 频率
- [ ] 手动 `db.flush()` 后 `rocksdb.num-files-at-level0` 增加
- [ ] `setMaxWriteBufferNumber` 过小会导致写阻塞

---

## 常见问题

**Q: `sync=true` 和 `disableWAL=true` 能同时设置吗？**
A: 逻辑上矛盾，但 API 允许。`disableWAL=true` 会覆盖 `sync` 的行为，实际无 WAL 写入。

**Q: MemTable 用的是什么数据结构？**
A: 默认是跳表（SkipList），可通过 `setMemTableConfig()` 改为 HashSkipList 或 VectorRep。

**Q: Flush 会阻塞写操作吗？**
A: 当 MemTable 数量达到 `max_write_buffer_number` 上限后，新的写操作会被阻塞直到有 MemTable 被 flush 完成。
