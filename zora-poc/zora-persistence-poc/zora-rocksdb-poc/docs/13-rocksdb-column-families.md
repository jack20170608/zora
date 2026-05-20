# 13 Column Families

## 目标

理解 Column Family（CF）的概念，掌握多 CF 的创建、打开、读写和删除操作。

---

## 步骤 1：理解 Column Family

Column Family 是 RocksDB 中独立的键空间（keyspace）。每个 CF 有自己的 MemTable 和 SST 文件，但共享同一个 WAL。CF 之间 key 可以重复，互不干扰。

---

## 步骤 2：创建和打开多 CF 数据库

### 2.1 首次创建（自动创建默认 CF）

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnFamilyTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateAndUseColumnFamilies() throws RocksDBException {
        RocksDB.loadLibrary();

        // First open: create default CF
        try (Options options = new Options().setCreateIfMissing(true)) {
            RocksDB db = RocksDB.open(options, tempDir.toString());
            db.close();
        }

        // Second open: list existing CFs and open with them
        List<byte[]> cfNames = RocksDB.listColumnFamilies(new Options(), tempDir.toString());
        assertThat(cfNames).hasSize(1);
        assertThat(new String(cfNames.get(0))).isEqualTo("default");
    }
}
```

### 2.2 创建新 CF 并读写

```java
@Test
void shouldCreateNewColumnFamily() throws RocksDBException {
    RocksDB.loadLibrary();

    // Open with default CF
    List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
    cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));

    List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

    try (DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);
         RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {

        ColumnFamilyHandle defaultCf = cfHandles.get(0);

        // Create a new column family
        ColumnFamilyDescriptor newCfDesc = new ColumnFamilyDescriptor("users".getBytes());
        ColumnFamilyHandle usersCf = db.createColumnFamily(newCfDesc);

        // Write to different CFs with same key
        db.put(defaultCf, "id".getBytes(), "default-value".getBytes());
        db.put(usersCf, "id".getBytes(), "user-value".getBytes());

        // Read back
        assertThat(new String(db.get(defaultCf, "id".getBytes()))).isEqualTo("default-value");
        assertThat(new String(db.get(usersCf, "id".getBytes()))).isEqualTo("user-value");

        usersCf.close();
    }
}
```

---

## 步骤 3：打开已存在 CF 的数据库

### 3.1 必须列出所有 CF

```java
@Test
void shouldReopenWithExistingColumnFamilies() throws RocksDBException {
    RocksDB.loadLibrary();

    String dbPath = tempDir.toString();

    // Phase 1: create DB and CFs
    {
        List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
        cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

        try (DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(dbOptions, dbPath, cfDescriptors, cfHandles)) {

            ColumnFamilyHandle usersCf = db.createColumnFamily(
                new ColumnFamilyDescriptor("users".getBytes())
            );
            ColumnFamilyHandle ordersCf = db.createColumnFamily(
                new ColumnFamilyDescriptor("orders".getBytes())
            );

            db.put(usersCf, "u1".getBytes(), "alice".getBytes());
            db.put(ordersCf, "o1".getBytes(), "100".getBytes());

            usersCf.close();
            ordersCf.close();
        }
    }

    // Phase 2: reopen with all CFs listed
    {
        List<byte[]> cfNames = RocksDB.listColumnFamilies(new Options(), dbPath);

        List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
        for (byte[] name : cfNames) {
            cfDescriptors.add(new ColumnFamilyDescriptor(name));
        }

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

        try (DBOptions dbOptions = new DBOptions();
             RocksDB db = RocksDB.open(dbOptions, dbPath, cfDescriptors, cfHandles)) {

            assertThat(cfHandles).hasSize(3);  // default, users, orders

            // Find handles by name
            ColumnFamilyHandle usersCf = null;
            ColumnFamilyHandle ordersCf = null;
            for (ColumnFamilyHandle handle : cfHandles) {
                String name = new String(handle.getName());
                if ("users".equals(name)) usersCf = handle;
                if ("orders".equals(name)) ordersCf = handle;
            }

            assertThat(usersCf).isNotNull();
            assertThat(ordersCf).isNotNull();

            assertThat(new String(db.get(usersCf, "u1".getBytes()))).isEqualTo("alice");
            assertThat(new String(db.get(ordersCf, "o1".getBytes()))).isEqualTo("100");
        }
    }
}
```

---

## 步骤 4：删除 CF

### 4.1 删除与验证

```java
@Test
void shouldDropColumnFamily() throws RocksDBException {
    RocksDB.loadLibrary();

    List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
    cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));

    List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

    try (DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);
         RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {

        ColumnFamilyHandle tempCf = db.createColumnFamily(
            new ColumnFamilyDescriptor("temp".getBytes())
        );

        db.put(tempCf, "key".getBytes(), "value".getBytes());
        assertThat(new String(db.get(tempCf, "key".getBytes()))).isEqualTo("value");

        db.dropColumnFamily(tempCf);
        tempCf.close();

        // After drop, the CF is gone
        List<byte[]> remaining = RocksDB.listColumnFamilies(new Options(), tempDir.toString());
        assertThat(remaining.stream()
            .map(String::new)
            .toList())
            .doesNotContain("temp");
    }
}
```

---

## 步骤 5：每个 CF 独立配置

### 5.1 不同 CF 使用不同选项

```java
@Test
void shouldUseDifferentOptionsPerCf() throws RocksDBException {
    RocksDB.loadLibrary();

    List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();

    ColumnFamilyOptions defaultCfOpts = new ColumnFamilyOptions();
    cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, defaultCfOpts));

    ColumnFamilyOptions usersCfOpts = new ColumnFamilyOptions()
        .setWriteBufferSize(64 * 1024 * 1024);  // 64MB memtable for users
    cfDescriptors.add(new ColumnFamilyDescriptor("users".getBytes(), usersCfOpts));

    List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

    try (DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);
         RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {

        assertThat(cfHandles).hasSize(2);

        // Cleanup
        for (ColumnFamilyHandle handle : cfHandles) {
            handle.close();
        }
    }

    defaultCfOpts.close();
    usersCfOpts.close();
}
```

---

## 验证检查清单

- [ ] 不同 CF 中相同 key 存储的值互不干扰
- [ ] `listColumnFamilies` 返回数据库中所有 CF 名称
- [ ] 重新打开数据库时必须列出所有已存在的 CF
- [ ] `dropColumnFamily` 后 CF 及其数据被删除
- [ ] 每个 CF 可独立配置 `ColumnFamilyOptions`

---

## 常见问题

**Q: 打开时漏列一个已存在的 CF 会怎样？**
A: 抛出 `RocksDBException: Invalid argument: Column family not found`。

**Q: 可以在数据库已打开时创建新 CF 吗？**
A: 可以，使用 `db.createColumnFamily(ColumnFamilyDescriptor)`，无需重启。

**Q: 最多能有多少个 CF？**
A: 没有硬性限制，但 CF 数量过多会增加内存和文件句柄开销，建议控制在几十个以内。
