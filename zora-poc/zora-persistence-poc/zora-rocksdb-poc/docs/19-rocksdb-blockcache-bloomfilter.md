# 19 Block Cache 与 Bloom Filter

## 目标

掌握 Block Cache 和 Bloom Filter 的配置方法，理解它们对读性能的影响，通过实验对比开启前后的性能差异。

---

## 步骤 1：理解 Block Cache

SST 文件由多个 Block 组成。读取一个 key 时，RocksDB 需要定位到包含该 key 的 Block 并读取。Block Cache 将最近访问的 Block 缓存在内存中，避免重复磁盘 I/O。

---

## 步骤 2：配置 LRU Block Cache

### 2.1 基础配置

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BlockCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUseBlockCache() throws RocksDBException {
        RocksDB.loadLibrary();

        // Create a 64MB LRU cache
        Cache cache = new LRUCache(64 * 1024 * 1024);

        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
            .setBlockCache(cache)
            .setBlockSize(16 * 1024);  // 16KB per block

        Options options = new Options()
            .setCreateIfMissing(true)
            .setTableFormatConfig(tableConfig);

        RocksDB db = RocksDB.open(options, tempDir.toString());

        // Write data
        for (int i = 0; i < 1000; i++) {
            db.put(("key" + i).getBytes(), ("value" + i).getBytes());
        }
        db.flush(new FlushOptions().setWaitForFlush(true));

        // First read: cache miss
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            db.get(("key" + i).getBytes());
        }
        long firstReadMs = System.currentTimeMillis() - t1;

        // Second read: cache hit
        long t2 = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            db.get(("key" + i).getBytes());
        }
        long secondReadMs = System.currentTimeMillis() - t2;

        System.out.println("First read (cold cache): " + firstReadMs + " ms");
        System.out.println("Second read (warm cache): " + secondReadMs + " ms");

        assertThat(secondReadMs).isLessThan(firstReadMs);

        db.close();
        options.close();
        cache.close();
    }
}
```

### 2.2 关键要点

| 要点 | 说明 |
|---|---|
| `LRUCache` | 固定大小的 LRU 淘汰缓存，需手动 `close()` |
| `setBlockSize` | SST 文件中每个 Block 的大小，默认 4KB，大值减少索引大小但增加读取放大 |
| `setBlockCache` | 将 Cache 绑定到 Table 配置，所有 CF 共享同一个 Cache 实例 |

---

## 步骤 3：配置 Bloom Filter

### 3.1 减少不存在 Key 的读放大

Bloom Filter 是一种概率型数据结构，用于快速判断一个 key "肯定不存在" 或 "可能存在"。对于不存在的 key，可以直接跳过 SST 文件的读取。

```java
@Test
void shouldUseBloomFilter() throws RocksDBException {
    RocksDB.loadLibrary();

    // Create Bloom Filter with 10 bits per key
    BloomFilter bloomFilter = new BloomFilter(10);

    BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
        .setFilterPolicy(bloomFilter);

    Options options = new Options()
        .setCreateIfMissing(true)
        .setTableFormatConfig(tableConfig);

    RocksDB db = RocksDB.open(options, tempDir.toString());

    // Write some data
    for (int i = 0; i < 1000; i++) {
        db.put(("key" + i).getBytes(), ("value" + i).getBytes());
    }
    db.flush(new FlushOptions().setWaitForFlush(true));

    // Query non-existing keys with Bloom Filter
    long t1 = System.currentTimeMillis();
    for (int i = 10000; i < 11000; i++) {
        db.get(("key" + i).getBytes());
    }
    long withBloomMs = System.currentTimeMillis() - t1;

    System.out.println("Non-existing key lookups with Bloom Filter: " + withBloomMs + " ms");

    db.close();
    options.close();
    bloomFilter.close();
}
```

### 3.2 Bloom Filter 对比

| 场景 | 无 Bloom Filter | 有 Bloom Filter |
|---|---|---|
| Key 存在 | 读 SST Block | 读 SST Block |
| Key 不存在 | 读每个 SST 文件 | 跳过大部分 SST 文件 |
| 内存开销 | 无 | 每个 SST 文件约 10 bits/key |

---

## 步骤 4：Cache 命中率监控

### 4.1 读取 Block Cache 统计

```java
@Test
void shouldMonitorCacheHitRate() throws RocksDBException {
        RocksDB.loadLibrary();

        Cache cache = new LRUCache(64 * 1024 * 1024);

        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
            .setBlockCache(cache);

        Options options = new Options()
            .setCreateIfMissing(true)
            .setTableFormatConfig(tableConfig)
            .setStatistics(new Statistics());

        RocksDB db = RocksDB.open(options, tempDir.toString());

        // Write and flush
        for (int i = 0; i < 500; i++) {
            db.put(("key" + i).getBytes(), ("value" + i).getBytes());
        }
        db.flush(new FlushOptions().setWaitForFlush(true));

        // Cold read
        for (int i = 0; i < 500; i++) {
            db.get(("key" + i).getBytes());
        }

        // Warm read
        for (int i = 0; i < 500; i++) {
            db.get(("key" + i).getBytes());
        }

        Statistics stats = options.statistics();
        long hits = stats.getTickerCount(TickerType.BLOCK_CACHE_HIT);
        long misses = stats.getTickerCount(TickerType.BLOCK_CACHE_MISS);
        double hitRate = (double) hits / (hits + misses) * 100;

        System.out.println("Block Cache Hits: " + hits);
        System.out.println("Block Cache Misses: " + misses);
        System.out.println("Hit Rate: " + String.format("%.2f%%", hitRate));

        assertThat(hits).isGreaterThan(0);
        assertThat(misses).isGreaterThan(0);

        db.close();
        options.close();
        cache.close();
}
```

### 4.2 关键 Ticker 指标

| Ticker | 含义 |
|---|---|
| `BLOCK_CACHE_HIT` | Block Cache 命中次数 |
| `BLOCK_CACHE_MISS` | Block Cache 未命中次数 |
| `BLOOM_FILTER_USEFUL` | Bloom Filter 成功避免 SST 读取的次数 |
| `BLOOM_FILTER_FULL_POSITIVE` | Bloom Filter 误判（假阳性）次数 |

---

## 步骤 5：综合配置示例

### 5.1 生产环境推荐配置

```java
@Test
void shouldConfigureForProduction() throws RocksDBException {
    RocksDB.loadLibrary();

    // Shared cache across all column families
    Cache cache = new LRUCache(512 * 1024 * 1024);  // 512MB

    BloomFilter bloomFilter = new BloomFilter(10);

    BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
        .setBlockCache(cache)
        .setBlockSize(16 * 1024)
        .setFilterPolicy(bloomFilter)
        .setCacheIndexAndFilterBlocks(true);  // Cache index/filter blocks in block cache

    ColumnFamilyOptions cfOptions = new ColumnFamilyOptions()
        .setTableFormatConfig(tableConfig);

    DBOptions dbOptions = new DBOptions()
        .setCreateIfMissing(true)
        .setStatistics(new Statistics());

    List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
    cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));

    List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

    try (RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {
        // Database is now configured with Block Cache and Bloom Filter
        assertThat(db).isNotNull();
    }

    for (ColumnFamilyHandle handle : cfHandles) {
        handle.close();
    }
    cfOptions.close();
    dbOptions.close();
    cache.close();
    bloomFilter.close();
}
```

---

## 验证检查清单

- [ ] 首次读取（冷缓存）慢于第二次读取（热缓存）
- [ ] Block Cache 命中次数大于 0
- [ ] Bloom Filter 开启后，不存在 key 的查询更快
- [ ] `BLOCK_CACHE_HIT` 和 `BLOCK_CACHE_MISS` 统计可正常读取
- [ ] `setCacheIndexAndFilterBlocks(true)` 将索引和过滤器也纳入缓存管理
- [ ] Cache 实例可在多个 CF / DB 间共享

---

## 常见问题

**Q: Block Cache 大小怎么估算？**
A: 通常设置为可用内存的 1/3 到 1/2，需为 OS 和 JVM 留出空间。

**Q: Bloom Filter 的 bits/key 越大越好吗？**
A: 越大误判率越低，但内存开销越大。10 bits/key 约 1% 误判率，是常用折中。

**Q: 为什么缓存命中后读还是慢？**
A: 可能是索引或过滤器未缓存（需设置 `setCacheIndexAndFilterBlocks(true)`）。
