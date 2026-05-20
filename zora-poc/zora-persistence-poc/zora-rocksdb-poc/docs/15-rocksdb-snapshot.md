# 15 Snapshot 快照读

## 目标

理解 Snapshot 的语义，掌握在并发写入场景下实现一致性读的方法。

---

## 步骤 1：Snapshot 的基本概念

Snapshot 是数据库在某一时刻的一致性视图。创建 Snapshot 后，即使其他线程/事务继续写入数据，通过该 Snapshot 读取的内容保持不变。

Snapshot 的实现基于 RocksDB 的 MVCC（Multi-Version Concurrency Control）机制。创建 Snapshot 时记录当前的 sequence number，读操作只返回 sequence number 小于等于该值的数据。

---

## 步骤 2：基础 Snapshot 读写

### 2.1 最小可运行示例

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadConsistentSnapshot() throws RocksDBException {
        RocksDB.loadLibrary();

        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        // Step 1: Write initial data
        db.put("key".getBytes(), "v1".getBytes());

        // Step 2: Create snapshot
        Snapshot snapshot = db.getSnapshot();

        // Step 3: Write new data AFTER snapshot creation
        db.put("key".getBytes(), "v2".getBytes());

        // Step 4: Read with snapshot -> should see v1
        try (ReadOptions readOptions = new ReadOptions().setSnapshot(snapshot)) {
            byte[] value = db.get(readOptions, "key".getBytes());
            assertThat(new String(value)).isEqualTo("v1");
        }

        // Step 5: Read without snapshot -> should see v2
        byte[] latestValue = db.get("key".getBytes());
        assertThat(new String(latestValue)).isEqualTo("v2");

        // Cleanup
        snapshot.close();
        db.close();
        options.close();
    }
}
```

### 2.2 关键要点

| 要点 | 说明 |
|---|---|
| `db.getSnapshot()` | 创建 Snapshot，记录当前 sequence number |
| `ReadOptions.setSnapshot()` | 在 ReadOptions 中绑定 Snapshot，后续读操作使用该视图 |
| `snapshot.close()` | 释放 Snapshot，允许 RocksDB 回收旧版本数据 |

---

## 步骤 3：Snapshot 与 Iterator 结合

### 3.1 在迭代器上使用 Snapshot

```java
@Test
void shouldIterateWithSnapshot() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, tempDir.toString());

    db.put("a".getBytes(), "1".getBytes());
    db.put("b".getBytes(), "2".getBytes());

    Snapshot snapshot = db.getSnapshot();

    // Add more data after snapshot
    db.put("c".getBytes(), "3".getBytes());
    db.put("d".getBytes(), "4".getBytes());

    // Iterate using snapshot - should only see a, b
    List<String> keys = new ArrayList<>();
    try (ReadOptions readOptions = new ReadOptions().setSnapshot(snapshot);
         RocksIterator it = db.newIterator(readOptions)) {

        it.seekToFirst();
        while (it.isValid()) {
            keys.add(new String(it.key()));
            it.next();
        }
    }

    assertThat(keys).containsExactly("a", "b");

    snapshot.close();
    db.close();
    options.close();
}
```

---

## 步骤 4：并发场景验证

### 4.1 多写单读一致性验证

```java
@Test
void shouldMaintainConsistencyUnderConcurrentWrites() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, tempDir.toString());

    // Pre-populate
    for (int i = 0; i < 10; i++) {
        db.put(("key" + i).getBytes(), ("v" + i).getBytes());
    }

    // Capture snapshot
    Snapshot snapshot = db.getSnapshot();

    // Simulate concurrent writes: overwrite all keys
    for (int i = 0; i < 10; i++) {
        db.put(("key" + i).getBytes(), ("new" + i).getBytes());
    }

    // Verify snapshot still sees original values
    try (ReadOptions readOptions = new ReadOptions().setSnapshot(snapshot)) {
        for (int i = 0; i < 10; i++) {
            byte[] value = db.get(readOptions, ("key" + i).getBytes());
            assertThat(new String(value)).isEqualTo("v" + i);
        }
    }

    // Verify latest read sees new values
    for (int i = 0; i < 10; i++) {
        byte[] value = db.get(("key" + i).getBytes());
        assertThat(new String(value)).isEqualTo("new" + i);
    }

    snapshot.close();
    db.close();
    options.close();
}
```

---

## 步骤 5：Snapshot 生命周期管理

### 5.1 及时释放的重要性

```java
@Test
void shouldReleaseSnapshot() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, tempDir.toString());

    Snapshot snapshot = db.getSnapshot();

    // Do some reads...
    try (ReadOptions readOptions = new ReadOptions().setSnapshot(snapshot)) {
        db.get(readOptions, "any".getBytes());
    }

    // Must release to allow compaction to remove old versions
    snapshot.close();

    db.close();
    options.close();
}
```

### 5.2 生命周期影响

| 行为 | 影响 |
|---|---|
| 持有 Snapshot 不释放 | 阻止 compaction 清理旧版本数据，导致磁盘空间膨胀 |
| 多个长期 Snapshot | 内存和磁盘空间持续增长，严重时影响性能 |
| 及时 `snapshot.close()` | 允许 compaction 回收资源，推荐在读操作完成后立即释放 |

---

## 验证检查清单

- [ ] Snapshot 创建后写入的新数据，通过 Snapshot 读取不可见
- [ ] Snapshot 创建后写入的新数据，不绑定 Snapshot 的读操作可见
- [ ] Iterator 绑定 Snapshot 后，迭代结果仅包含 Snapshot 时刻的数据
- [ ] `snapshot.close()` 后旧版本数据可被 compaction 回收
- [ ] 并发写入场景下 Snapshot 读保持一致性视图

---

## 常见问题

**Q: Snapshot 有数量限制吗？**
A: 没有硬性数量限制，但每个 Snapshot 都会阻止旧版本数据的清理，Snapshot 越多、持有时间越长，空间放大越严重。

**Q: Snapshot 是深拷贝吗？**
A: 不是。Snapshot 只是记录了一个 sequence number，底层数据共享，因此创建开销极小。

**Q: Snapshot 能跨实例使用吗？**
A: 不能。Snapshot 只能在创建它的 `RocksDB` 实例上使用。
