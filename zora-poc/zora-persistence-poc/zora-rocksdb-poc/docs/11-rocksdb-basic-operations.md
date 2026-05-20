# 11 RocksDB 基础操作

## 目标

掌握 RocksDB 实例的打开、关闭，以及最基本的 Put / Get / Delete 操作。

---

## 步骤 1：环境准备

### 1.1 确认依赖

确保 `pom.xml` 中已引入 `rocksdbjni`：

```xml
<dependency>
    <groupId>org.rocksdb</groupId>
    <artifactId>rocksdbjni</artifactId>
    <version>9.10.0</version>
</dependency>
```

### 1.2 加载原生库

在使用任何 RocksDB API 之前，必须先加载原生动态库：

```java
RocksDB.loadLibrary();
```

> 建议在测试类的 `@BeforeAll` 中调用一次即可，或在 `static` 块中调用。

---

## 步骤 2：打开与关闭数据库

### 2.1 最小可运行示例

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BasicOperationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldOpenAndCloseDb() throws RocksDBException {
        // Load native library
        RocksDB.loadLibrary();

        // Configure options
        Options options = new Options().setCreateIfMissing(true);

        // Open database
        RocksDB db = RocksDB.open(options, tempDir.toString());
        assertThat(db).isNotNull();

        // Close in reverse order
        db.close();
        options.close();
    }
}
```

### 2.2 关键要点

| 要点 | 说明 |
|---|---|
| `setCreateIfMissing(true)` | 数据库目录不存在时自动创建，**首次运行必须设置**。 |
| 关闭顺序 | 先 `db.close()`，再 `options.close()`。RocksDB 的 C++ 对象需要手动释放，否则可能内存泄漏。 |
| `RocksDBException` | 所有 I/O 相关操作均可能抛出，需捕获或声明。 |

---

## 步骤 3：Put / Get / Delete

### 3.1 完整 CRUD 测试

```java
@Test
void shouldPutGetAndDelete() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, tempDir.toString());

    byte[] key = "hello".getBytes();
    byte[] value = "world".getBytes();

    // Put
    db.put(key, value);

    // Get
    byte[] retrieved = db.get(key);
    assertThat(new String(retrieved)).isEqualTo("world");

    // Delete
    db.delete(key);

    // Verify deletion
    byte[] afterDelete = db.get(key);
    assertThat(afterDelete).isNull();

    db.close();
    options.close();
}
```

### 3.2 批量 Get

```java
@Test
void shouldGetMultipleKeys() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options().setCreateIfMissing(true);
    RocksDB db = RocksDB.open(options, tempDir.toString());

    db.put("k1".getBytes(), "v1".getBytes());
    db.put("k2".getBytes(), "v2".getBytes());
    db.put("k3".getBytes(), "v3".getBytes());

    List<byte[]> keys = List.of("k1".getBytes(), "k2".getBytes(), "k3".getBytes());
    List<byte[]> values = db.multiGetAsList(keys);

    assertThat(values).hasSize(3);
    assertThat(new String(values.get(0))).isEqualTo("v1");
    assertThat(new String(values.get(1))).isEqualTo("v2");
    assertThat(new String(values.get(2))).isEqualTo("v3");

    db.close();
    options.close();
}
```

---

## 步骤 4：常用配置项实验

### 4.1 测试代码

```java
@Test
void shouldConfigureDbOptions() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options()
        .setCreateIfMissing(true)
        .setDbWriteBufferSize(64 * 1024 * 1024)  // 64MB
        .setMaxOpenFiles(1000)
        .setMaxBackgroundJobs(4);

    RocksDB db = RocksDB.open(options, tempDir.toString());
    assertThat(db).isNotNull();

    db.close();
    options.close();
}
```

### 4.2 配置项速查

| 配置项 | 默认值 | 作用 |
|---|---|---|
| `setCreateIfMissing` | false | 目录不存在时是否创建数据库 |
| `setDbWriteBufferSize` | 0 (不限制) | 所有 MemTable 的内存上限 |
| `setMaxOpenFiles` | -1 (无限制) | 最大同时打开的文件数 |
| `setMaxBackgroundJobs` | 2 | 后台线程数（flush + compaction） |

---

## 验证检查清单

- [ ] `RocksDB.loadLibrary()` 调用后无 `RocksDBException`
- [ ] 成功 `open` 并 `close` 数据库，进程退出后目录下生成 `CURRENT`、`IDENTITY`、`MANIFEST-*` 等文件
- [ ] Put 后 Get 返回相同值
- [ ] Delete 后 Get 返回 `null`
- [ ] `multiGetAsList` 返回与请求 key 数量一致的结果列表
- [ ] 关闭顺序错误（先关 options 再关 db）不会导致 JVM 崩溃

---

## 常见问题

**Q: 提示 `RocksDBException: Invalid argument: ` 或 `LOCK` 文件冲突？**
A: 同一目录不能同时被两个 RocksDB 实例打开。确保前一个实例已 `close()`，或更换 `@TempDir`。

**Q: 进程退出后数据丢失？**
A: 默认 WAL（Write Ahead Log）是开启的，正常关闭不会丢数据。但如果 `WriteOptions.setSync(false)` 且系统突然断电，最后几秒数据可能丢失。
