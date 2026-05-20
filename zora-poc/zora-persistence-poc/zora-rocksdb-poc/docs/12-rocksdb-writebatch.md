# 12 WriteBatch 批量写入

## 目标

掌握 `WriteBatch` 的使用方法，理解批量写入的原子性保证，并对比其与逐条写入的性能差异。

---

## 步骤 1：基础 WriteBatch

### 1.1 最小可运行示例

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WriteBatchTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldBatchPutAndDelete() throws RocksDBException {
        RocksDB.loadLibrary();

        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        try (WriteBatch batch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions()) {

            batch.put("key1".getBytes(), "value1".getBytes());
            batch.put("key2".getBytes(), "value2".getBytes());
            batch.put("key3".getBytes(), "value3".getBytes());
            batch.delete("key2".getBytes());

            db.write(writeOptions, batch);
        }

        assertThat(new String(db.get("key1".getBytes()))).isEqualTo("value1");
        assertThat(db.get("key2".getBytes())).isNull();  // deleted in batch
        assertThat(new String(db.get("key3".getBytes()))).isEqualTo("value3");

        db.close();
        options.close();
    }
}
```

### 1.2 关键要点

| 要点 | 说明 |
|---|---|
| `WriteBatch` 的 `close()` | WriteBatch 内部持有 C++ 对象，必须用 try-with-resources 或手动 `close()`。 |
| 原子性 | `db.write()` 会将整个 Batch 作为一条 WAL 记录写入，要么全部成功，要么全部失败。 |
| 顺序性 | Batch 内操作按添加顺序执行，`delete` 在同 `key` 的 `put` 之后则最终为删除状态。 |

---

## 步骤 2：原子性验证

### 2.1 测试代码

```java
@Test
void shouldBeAtomic() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, tempDir.toString());

    // Pre-populate key1
    db.put("key1".getBytes(), "old".getBytes());

    try (WriteBatch batch = new WriteBatch();
         WriteOptions writeOptions = new WriteOptions()) {

        batch.put("key1".getBytes(), "new".getBytes());
        batch.put("key2".getBytes(), "value2".getBytes());
        // Simulate: if an error occurs here, the whole batch should not be applied
    }

    // Without calling db.write(), nothing should change
    assertThat(new String(db.get("key1".getBytes()))).isEqualTo("old");
    assertThat(db.get("key2".getBytes())).isNull();

    db.close();
    options.close();
}
```

---

## 步骤 3：性能对比

### 3.1 Benchmark 测试

```java
@Test
void shouldBeFasterThanIndividualPuts() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, tempDir.toString());

    int count = 1000;
    byte[] value = "x".repeat(100).getBytes();

    // Individual puts
    long start1 = System.currentTimeMillis();
    for (int i = 0; i < count; i++) {
        db.put(("key" + i).getBytes(), value);
    }
    long duration1 = System.currentTimeMillis() - start1;

    // Reset db
    for (int i = 0; i < count; i++) {
        db.delete(("key" + i).getBytes());
    }

    // WriteBatch
    long start2 = System.currentTimeMillis();
    try (WriteBatch batch = new WriteBatch();
         WriteOptions writeOptions = new WriteOptions()) {
        for (int i = 0; i < count; i++) {
            batch.put(("key" + i).getBytes(), value);
        }
        db.write(writeOptions, batch);
    }
    long duration2 = System.currentTimeMillis() - start2;

    System.out.println("Individual puts: " + duration1 + " ms");
    System.out.println("WriteBatch: " + duration2 + " ms");

    assertThat(duration2).isLessThan(duration1);

    db.close();
    options.close();
}
```

### 3.2 性能差异原因

| 维度 | 逐条 Put | WriteBatch |
|---|---|---|
| WAL 写入次数 | 每条一次 fsync（或分组） | 整个 Batch 一次 |
| 系统调用 | N 次 write | 1 次 write |
| MemTable 插入 | 逐条触发 | 批量插入，减少锁竞争 |

---

## 步骤 4：WriteOptions 调优

### 4.1 同步写 vs 异步写

```java
@Test
void shouldControlSyncBehavior() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, tempDir.toString());

    try (WriteBatch batch = new WriteBatch()) {
        batch.put("key".getBytes(), "value".getBytes());

        // sync=false (default): faster, but may lose data on crash
        try (WriteOptions async = new WriteOptions().setSync(false)) {
            db.write(async, batch);
        }

        // sync=true: slower, guarantees durability
        try (WriteOptions sync = new WriteOptions().setSync(true)) {
            db.write(sync, batch);
        }

        // disable WAL entirely: fastest, but no crash recovery
        try (WriteOptions noWal = new WriteOptions().setDisableWAL(true)) {
            db.write(noWal, batch);
        }
    }

    db.close();
    options.close();
}
```

### 4.2 选项速查

| 选项 | 默认值 | 作用 |
|---|---|---|
| `setSync` | false | 是否每次写入后调用 `fsync`，保证 OS 刷盘 |
| `setDisableWAL` | false | 是否禁用 WAL，仅写 MemTable |

---

## 验证检查清单

- [ ] WriteBatch 内多条 put + delete 在一次 `db.write()` 后全部生效
- [ ] 未调用 `db.write()` 时，Batch 内操作不会应用到数据库
- [ ] WriteBatch 性能显著优于逐条 Put（1000 条以上差距明显）
- [ ] `setSync(true)` 写入后即使进程崩溃数据也不丢失
- [ ] `setDisableWAL(true)` 写入后进程崩溃数据丢失

---

## 常见问题

**Q: WriteBatch 有大小限制吗？**
A: 没有硬性上限，但过大的 Batch 会占用大量内存，且 WAL 写入耗时增加。建议根据实际场景控制在几 MB 到几十 MB。

**Q: WriteBatch 可以跨多个 Column Family 吗？**
A: 可以。`WriteBatch` 提供了 `put(ColumnFamilyHandle, byte[], byte[])` 等重载方法。
