# 22 自定义 Comparator 与 Merge Operator

## 目标

了解 RocksDB 的高级扩展点，掌握自定义 Comparator 和 Merge Operator 的实现方法。

---

## 步骤 1：自定义 Comparator

### 1.1 实现逆序比较器

RocksDB 默认按 key 的字节升序排列。通过自定义 Comparator 可以实现逆序、复合 key 等排序逻辑。

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomExtensionsTest {

    @TempDir
    Path tempDir;

    /**
     * Reverse comparator: sorts keys in descending order.
     */
    static class ReverseComparator extends AbstractComparator {

        @Override
        public String name() {
            return "ReverseComparator";
        }

        @Override
        public int compare(Slice a, Slice b) {
            // Compare byte by byte in reverse order
            return -ByteBuffer.wrap(a.data()).compareTo(ByteBuffer.wrap(b.data()));
        }
    }

    @Test
    void shouldUseReverseComparator() throws RocksDBException {
        RocksDB.loadLibrary();

        ReverseComparator comparator = new ReverseComparator();

        ColumnFamilyOptions cfOptions = new ColumnFamilyOptions()
            .setComparator(comparator);

        DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);

        List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
        cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

        try (RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {
            db.put("a".getBytes(), "1".getBytes());
            db.put("b".getBytes(), "2".getBytes());
            db.put("c".getBytes(), "3".getBytes());

            List<String> keys = new ArrayList<>();
            try (RocksIterator it = db.newIterator()) {
                it.seekToFirst();  // In reverse order, "c" comes first
                while (it.isValid()) {
                    keys.add(new String(it.key()));
                    it.next();
                }
            }

            // Keys should be in reverse order: c, b, a
            assertThat(keys).containsExactly("c", "b", "a");
        }

        cfOptions.close();
        dbOptions.close();
        comparator.close();
    }
}
```

### 1.2 Comparator 要点

| 要点 | 说明 |
|---|---|
| `name()` | 返回比较器名称，RocksDB 将其持久化到 SST 文件中 |
| `compare(Slice, Slice)` | 返回负数/零/正数，对应小于/等于/大于 |
| 一致性要求 | 必须满足严格弱序：自反、反对称、传递 |
| 持久化 | Comparator 名称保存在 SST 元数据中，打开时必须提供同名比较器 |

---

## 步骤 2：自定义 Merge Operator

### 2.1 实现计数器累加

Merge Operator 允许将增量更新合并到已有值，避免 read-modify-write 开销。

```java
/**
 * Merge operator for a simple counter.
 * Values are stored as 8-byte long integers.
 * Merge operation adds the delta to the existing value.
 */
static class CounterMergeOperator extends AbstractMergeOperator {

    @Override
    public String name() {
        return "CounterMergeOperator";
    }

    @Override
    protected MergeOperatorFullMergeV2Result fullMergeV2(MergeOperationInput mergeInput,
                                                          MergeOperationOutput mergeOutput) {
        long sum = 0;

        // If there's an existing value, add it
        if (mergeInput.existingValue() != null && mergeInput.existingValue().length == 8) {
            sum += bytesToLong(mergeInput.existingValue());
        }

        // Add all operands (deltas)
        for (byte[] operand : mergeInput.operandList()) {
            if (operand.length == 8) {
                sum += bytesToLong(operand);
            }
        }

        mergeOutput.setValue(longToBytes(sum));
        return MergeOperatorFullMergeV2Result.success();
    }

    @Override
    protected boolean partialMergeMulti(byte[][] operands,
                                        List<byte[]> resultOperands) {
        // Optimize: merge multiple deltas into one
        long sum = 0;
        for (byte[] operand : operands) {
            if (operand.length == 8) {
                sum += bytesToLong(operand);
            }
        }
        resultOperands.add(longToBytes(sum));
        return true;
    }

    private static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return buffer.getLong();
    }

    private static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }
}
```

### 2.2 使用 Merge Operator

```java
@Test
void shouldUseCounterMergeOperator() throws RocksDBException {
    RocksDB.loadLibrary();

    CounterMergeOperator mergeOperator = new CounterMergeOperator();

    ColumnFamilyOptions cfOptions = new ColumnFamilyOptions()
        .setMergeOperator(mergeOperator);

    DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);

    List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
    cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));

    List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

    try (RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {
        byte[] key = "counter".getBytes();
        byte[] delta10 = longToBytes(10L);
        byte[] delta20 = longToBytes(20L);
        byte[] delta30 = longToBytes(30L);

        // Initial put
        db.put(key, longToBytes(100L));

        // Merge deltas
        db.merge(key, delta10);
        db.merge(key, delta20);
        db.merge(key, delta30);

        // Read merged result
        byte[] result = db.get(key);
        long counterValue = bytesToLong(result);

        // 100 + 10 + 20 + 30 = 160
        assertThat(counterValue).isEqualTo(160L);
    }

    cfOptions.close();
    dbOptions.close();
    mergeOperator.close();
}
```

---

## 步骤 3：Merge Operator 原理

### 3.1 合并触发时机

```
Client Merge("key", delta1)
Client Merge("key", delta2)
Client Merge("key", delta3)
    |
    v  (lazy merge - operands stored as-is)
MemTable: [key->delta1, key->delta2, key->delta3]
    |
    v  (flush/compaction triggers fullMerge)
SST File: [key->fullMerge(existingValue, [delta1, delta2, delta3])]
```

### 3.2 合并策略对比

| 策略 | 优点 | 缺点 |
|---|---|---|
| 传统 RMW | 简单直接 | 每次更新需读取旧值，写放大 |
| Merge Operator | 延迟合并，减少读放大 | 读取未合并数据时需实时计算 |

---

## 步骤 4：复合 Key Comparator

### 4.1 按用户ID+时间戳排序

```java
/**
 * Composite key: userId (8 bytes) + timestamp (8 bytes)
 * Sort by userId ascending, then by timestamp descending.
 */
static class UserTimestampComparator extends AbstractComparator {

    @Override
    public String name() {
        return "UserTimestampComparator";
        }

    @Override
    public int compare(Slice a, Slice b) {
        ByteBuffer bufA = ByteBuffer.wrap(a.data());
        ByteBuffer bufB = ByteBuffer.wrap(b.data());

        long userIdA = bufA.getLong();
        long userIdB = bufB.getLong();

        if (userIdA != userIdB) {
            return Long.compare(userIdA, userIdB);
        }

        long timestampA = bufA.getLong();
        long timestampB = bufB.getLong();

        // Timestamp descending (newer first)
        return Long.compare(timestampB, timestampA);
    }
}
```

---

## 验证检查清单

- [ ] 自定义 Comparator 后，Iterator 遍历顺序符合预期
- [ ] 自定义 Comparator 名称与 SST 文件中持久化的名称匹配（否则无法打开 DB）
- [ ] Merge Operator 的 `fullMergeV2` 正确处理 existing value 和多个 operands
- [ ] `partialMergeMulti` 将多个 operands 合并为更少（或一个）operand
- [ ] Merge 多次后读取结果等于各增量之和
- [ ] Flush/Compaction 后合并结果持久化为最终值

---

## 常见问题

**Q: Comparator 名称可以随便改吗？**
A: 不行。已持久化的 SST 文件中保存了 Comparator 名称，改名后无法打开已有数据库。

**Q: Merge Operator 的 operands 会永久保留吗？**
A: 不会。Flush 或 Compaction 时会调用 `fullMergeV2`，将 operands 和 existing value 合并为最终结果。

**Q: 可以同时使用自定义 Comparator 和 Merge Operator 吗？**
A: 可以，二者在 ColumnFamilyOptions 中分别设置，互不干扰。
