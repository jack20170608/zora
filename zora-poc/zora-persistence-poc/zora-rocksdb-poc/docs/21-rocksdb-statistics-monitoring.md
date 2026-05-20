# 21 统计与监控

## 目标

了解如何获取 RocksDB 的运行时统计信息，掌握关键性能指标的解读方法。

---

## 步骤 1：启用 Statistics

### 1.1 基础配置

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsMonitoringTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCollectStatistics() throws RocksDBException {
        RocksDB.loadLibrary();

        Statistics statistics = new Statistics();

        Options options = new Options()
            .setCreateIfMissing(true)
            .setStatistics(statistics);

        RocksDB db = RocksDB.open(options, tempDir.toString());

        // Write some data
        for (int i = 0; i < 100; i++) {
            db.put(("key" + i).getBytes(), ("value" + i).getBytes());
        }
        db.flush(new FlushOptions().setWaitForFlush(true));

        // Read to generate cache/miss stats
        for (int i = 0; i < 100; i++) {
            db.get(("key" + i).getBytes());
        }

        // Print key metrics
        long blockCacheHits = statistics.getTickerCount(TickerType.BLOCK_CACHE_HIT);
        long blockCacheMisses = statistics.getTickerCount(TickerType.BLOCK_CACHE_MISS);
        long bytesWritten = statistics.getTickerCount(TickerType.BYTES_WRITTEN);
        long bytesRead = statistics.getTickerCount(TickerType.BYTES_READ);
        long writes = statistics.getTickerCount(TickerType.NUMBER_KEYS_WRITTEN);
        long reads = statistics.getTickerCount(TickerType.NUMBER_KEYS_READ);

        System.out.println("=== Ticker Statistics ===");
        System.out.println("Block Cache Hits: " + blockCacheHits);
        System.out.println("Block Cache Misses: " + blockCacheMisses);
        System.out.println("Bytes Written: " + bytesWritten);
        System.out.println("Bytes Read: " + bytesRead);
        System.out.println("Keys Written: " + writes);
        System.out.println("Keys Read: " + reads);

        assertThat(writes).isEqualTo(100);
        assertThat(reads).isEqualTo(100);

        db.close();
        options.close();
        statistics.close();
    }
}
```

---

## 步骤 2：读取详细 Stats 报告

### 2.1 打印完整 Stats

```java
@Test
void shouldPrintDetailedStats() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options()
        .setCreateIfMissing(true)
        .setStatistics(new Statistics());

    RocksDB db = RocksDB.open(options, tempDir.toString());

    // Write data
    for (int i = 0; i < 1000; i++) {
        db.put(("key" + i).getBytes(), new byte[1024]);
    }
    db.flush(new FlushOptions().setWaitForFlush(true));

    // Wait for compaction
    try {
        Thread.sleep(2000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    // Get detailed stats string
    String stats = db.getProperty("rocksdb.stats");
    System.out.println("=== rocksdb.stats ===");
    System.out.println(stats);

    // Get aggregated table properties
    String tableProperties = db.getProperty("rocksdb.aggregated-table-properties");
    System.out.println("=== Table Properties ===");
    System.out.println(tableProperties);

    db.close();
    options.close();
}
```

### 2.2 Stats 输出解读

```
** Compaction Stats [default] **
Level    Files   Size(MB)  Score Read(GB)  Rn(GB) Rnp1(GB) ...
----------------------------------------------------------------
  L0      2       2.0      2.0     0.0      0.0     0.0
  L1      1      10.0      1.0     0.5      0.2     0.3
```

```
** DB Stats **
Uptime(secs): 10.0 total, 0.0 interval
Cumulative writes: 1000, 1000 keys
Cumulative WAL: 1000 writes, 0 syncs
```

---

## 步骤 3：Histogram 指标

### 3.1 读取延迟分布

```java
@Test
void shouldMeasureLatencyHistograms() throws RocksDBException {
    RocksDB.loadLibrary();

    Statistics statistics = new Statistics();

    Options options = new Options()
        .setCreateIfMissing(true)
        .setStatistics(statistics);

    RocksDB db = RocksDB.open(options, tempDir.toString());

    // Write data
    for (int i = 0; i < 500; i++) {
        db.put(("key" + i).getBytes(), ("value" + i).getBytes());
    }
    db.flush(new FlushOptions().setWaitForFlush(true));

    // Read to populate histograms
    for (int i = 0; i < 500; i++) {
        db.get(("key" + i).getBytes());
    }

    // Get histogram for get operations
    HistogramData getHistogram = statistics.getHistogramData(HistogramType.DB_GET);
    System.out.println("=== DB_GET Histogram ===");
    System.out.println("Average: " + getHistogram.getAverage() + " us");
    System.out.println("Median: " + getHistogram.getMedian() + " us");
    System.out.println("P95: " + getHistogram.getPercentile95() + " us");
    System.out.println("P99: " + getHistogram.getPercentile99() + " us");
    System.out.println("Max: " + getHistogram.getMax() + " us");

    assertThat(getHistogram.getAverage()).isGreaterThan(0);

    db.close();
    options.close();
    statistics.close();
}
```

### 3.2 常用 Histogram 类型

| HistogramType | 含义 |
|---|---|
| `DB_GET` | Get 操作延迟 |
| `DB_WRITE` | Write 操作延迟 |
| `DB_SEEK` | Iterator seek 延迟 |
| `COMPACTION_TIME` | Compaction 耗时 |
| `FLUSH_TIME` | Flush 耗时 |
| `SST_READ_MICROS` | SST 文件读取耗时 |

---

## 步骤 4：Level 与文件统计

### 4.1 各层文件数与大小

```java
@Test
void shouldMonitorLevelStats() throws RocksDBException {
    RocksDB.loadLibrary();

    Options options = new Options()
        .setCreateIfMissing(true)
        .setWriteBufferSize(1 * 1024 * 1024)
        .setTargetFileSizeBase(1 * 1024 * 1024);

    RocksDB db = RocksDB.open(options, tempDir.toString());

    // Write enough data to create multi-level SST files
    byte[] value = new byte[1024 * 1024];  // 1MB
    for (int i = 0; i < 100; i++) {
        db.put(("key" + String.format("%04d", i)).getBytes(), value);
    }

    db.flush(new FlushOptions().setWaitForFlush(true));

    // Wait for compaction
    try {
        Thread.sleep(3000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    System.out.println("=== Level Statistics ===");
    for (int level = 0; level <= 6; level++) {
        String numFiles = db.getProperty("rocksdb.num-files-at-level" + level);
        String levelSize = db.getProperty("rocksdb.size-at-level" + level);
        System.out.println("Level " + level + ": " + numFiles + " files, " + levelSize + " bytes");
    }

    String liveSstSize = db.getProperty("rocksdb.live-sst-files-size");
    String totalSstSize = db.getProperty("rocksdb.total-sst-files-size");
    System.out.println("Live SST size: " + liveSstSize);
    System.out.println("Total SST size: " + totalSstSize);

    db.close();
    options.close();
}
```

---

## 步骤 5：定期监控集成建议

### 5.1 生产环境监控指标

```java
// 建议定期采集的指标（非测试代码，仅示意）
public class RocksDbMonitor {

    public static void collectMetrics(RocksDB db, Statistics stats) throws RocksDBException {
        // Write/Read throughput
        long keysWritten = stats.getTickerCount(TickerType.NUMBER_KEYS_WRITTEN);
        long keysRead = stats.getTickerCount(TickerType.NUMBER_KEYS_READ);

        // Cache performance
        long cacheHits = stats.getTickerCount(TickerType.BLOCK_CACHE_HIT);
        long cacheMisses = stats.getTickerCount(TickerType.BLOCK_CACHE_MISS);
        double hitRate = cacheHits + cacheMisses > 0
            ? (double) cacheHits / (cacheHits + cacheMisses) * 100
            : 0;

        // Compaction activity
        long compactedBytes = stats.getTickerCount(TickerType.COMPACT_READ_BYTES);

        // Latency
        HistogramData getLatency = stats.getHistogramData(HistogramType.DB_GET);

        // Emit to monitoring system (e.g., Prometheus, Micrometer)
        // metrics.gauge("rocksdb.cache.hit.rate", hitRate);
        // metrics.gauge("rocksdb.get.p99.latency", getLatency.getPercentile99());
    }
}
```

---

## 验证检查清单

- [ ] `setStatistics()` 后 Ticker 计数正常增长
- [ ] `rocksdb.stats` 输出包含 Compaction 和各层文件信息
- [ ] Histogram 数据包含 Average / Median / P95 / P99 / Max
- [ ] 多层数据写入后各层文件数和大小可被读取
- [ ] Block Cache hit/miss 统计可用于计算命中率

---

## 常见问题

**Q: Statistics 有性能开销吗？**
A: 有少量原子操作开销，通常可忽略。若对性能极度敏感，可在生产环境关闭部分统计。

**Q: `rocksdb.stats` 输出格式会变化吗？**
A: 可能随版本变化，建议程序解析时做兼容性处理，或直接读取 Ticker/Histogram API。

**Q: 如何重置统计？**
A: `statistics.resetTickerCounts()` 可重置计数器，但通常建议周期性采样差值。
