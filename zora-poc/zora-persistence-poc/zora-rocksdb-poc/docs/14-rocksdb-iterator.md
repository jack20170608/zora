# 14 Iterator 与范围扫描

## 目标

掌握 `RocksIterator` 的正向/反向迭代、`seek` 定位以及范围扫描实现。

---

## 步骤 1：基础迭代

### 1.1 正向遍历

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IteratorTest {

    @TempDir
    Path tempDir;

    private RocksDB prepareDb() throws RocksDBException {
        RocksDB.loadLibrary();
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        db.put("a".getBytes(), "1".getBytes());
        db.put("b".getBytes(), "2".getBytes());
        db.put("c".getBytes(), "3".getBytes());
        db.put("d".getBytes(), "4".getBytes());

        return db;
    }

    @Test
    void shouldIterateForward() throws RocksDBException {
        RocksDB db = prepareDb();

        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();

        try (RocksIterator it = db.newIterator()) {
            it.seekToFirst();
            while (it.isValid()) {
                keys.add(new String(it.key()));
                values.add(new String(it.value()));
                it.next();
            }
        }

        assertThat(keys).containsExactly("a", "b", "c", "d");
        assertThat(values).containsExactly("1", "2", "3", "4");

        db.close();
    }
}
```

### 1.2 反向遍历

```java
@Test
void shouldIterateBackward() throws RocksDBException {
    RocksDB db = prepareDb();

    List<String> keys = new ArrayList<>();

    try (RocksIterator it = db.newIterator()) {
        it.seekToLast();
        while (it.isValid()) {
            keys.add(new String(it.key()));
            it.prev();
        }
    }

    assertThat(keys).containsExactly("d", "c", "b", "a");

    db.close();
}
```

---

## 步骤 2：Seek 定位

### 2.1 跳转到指定 Key

```java
@Test
void shouldSeekToKey() throws RocksDBException {
    RocksDB db = prepareDb();

    try (RocksIterator it = db.newIterator()) {
        it.seek("b".getBytes());
        assertThat(it.isValid()).isTrue();
        assertThat(new String(it.key())).isEqualTo("b");
        assertThat(new String(it.value())).isEqualTo("2");
    }

    db.close();
}
```

### 2.2 Seek 到不存在的 Key

```java
@Test
void shouldSeekToNextExistingKey() throws RocksDBException {
    RocksDB db = prepareDb();

    try (RocksIterator it = db.newIterator()) {
        // "bb" does not exist, seek will land on the next key: "c"
        it.seek("bb".getBytes());
        assertThat(it.isValid()).isTrue();
        assertThat(new String(it.key())).isEqualTo("c");
    }

    db.close();
}
```

---

## 步骤 3：范围扫描

### 3.1 指定起始和结束 Key

```java
@Test
void shouldScanRange() throws RocksDBException {
    RocksDB db = prepareDb();

    List<String> result = new ArrayList<>();

    try (RocksIterator it = db.newIterator()) {
        it.seek("b".getBytes());
        while (it.isValid()) {
            String key = new String(it.key());
            if (key.compareTo("d") > 0) {
                break;
            }
            result.add(key + "=" + new String(it.value()));
            it.next();
        }
    }

    assertThat(result).containsExactly("b=2", "c=3", "d=4");

    db.close();
}
```

---

## 步骤 4：前缀扫描

### 4.1 使用前缀 Seek

```java
@Test
void shouldScanByPrefix() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, tempDir.toString());

    db.put("user:alice".getBytes(), "data1".getBytes());
    db.put("user:bob".getBytes(), "data2".getBytes());
    db.put("user:charlie".getBytes(), "data3".getBytes());
    db.put("order:o1".getBytes(), "order1".getBytes());
    db.put("order:o2".getBytes(), "order2".getBytes());

    List<String> userKeys = new ArrayList<>();

    try (RocksIterator it = db.newIterator()) {
        it.seek("user:".getBytes());
        while (it.isValid()) {
            String key = new String(it.key());
            if (!key.startsWith("user:")) {
                break;
            }
            userKeys.add(key);
            it.next();
        }
    }

    assertThat(userKeys).containsExactly("user:alice", "user:bob", "user:charlie");

    db.close();
    options.close();
}
```

---

## 步骤 5：迭代器与 Column Family

### 5.1 在指定 CF 上迭代

```java
@Test
void shouldIterateOverColumnFamily() throws RocksDBException {
    RocksDB.loadLibrary();

    List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
    cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));

    List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

    try (DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);
         RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {

        ColumnFamilyHandle usersCf = db.createColumnFamily(
            new ColumnFamilyDescriptor("users".getBytes())
        );

        db.put(usersCf, "u1".getBytes(), "alice".getBytes());
        db.put(usersCf, "u2".getBytes(), "bob".getBytes());

        List<String> keys = new ArrayList<>();
        try (RocksIterator it = db.newIterator(usersCf)) {
            it.seekToFirst();
            while (it.isValid()) {
                keys.add(new String(it.key()));
                it.next();
            }
        }

        assertThat(keys).containsExactly("u1", "u2");

        usersCf.close();
    }
}
```

---

## 验证检查清单

- [ ] `seekToFirst()` 从最小 key 开始正向遍历
- [ ] `seekToLast()` 从最大 key 开始反向遍历
- [ ] `seek(key)` 定位到指定 key，若不存在则定位到下一个更大的 key
- [ ] 范围扫描通过 `seek(start)` + `next()` + `key > end` 判断退出实现
- [ ] 前缀扫描通过 `seek(prefix)` + `startsWith(prefix)` 判断退出实现
- [ ] 迭代器必须使用 try-with-resources 关闭

---

## 常见问题

**Q: 迭代过程中数据被修改会怎样？**
A: 迭代器持有创建时刻的 snapshot，不会看到后续写入的新数据（如果新数据在更高层则可能被看到，取决于实现细节）。

**Q: 迭代器可以同时在多个线程使用吗？**
A: `RocksIterator` 不是线程安全的，每个线程应创建独立的迭代器。
