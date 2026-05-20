package top.ilovemyhome.zora.poc.persistence.rocksdb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Statistics, histograms and property monitoring tests.
 */
class StatisticsMonitoringTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldCollectStatistics() throws RocksDBException {
        Statistics statistics = new Statistics();

        Options options = new Options()
            .setCreateIfMissing(true)
            .setStatistics(statistics);

        RocksDB db = RocksDB.open(options, tempDir.toString());

        for (int i = 0; i < 100; i++) {
            db.put(("key" + i).getBytes(), ("value" + i).getBytes());
        }
        db.flush(new FlushOptions().setWaitForFlush(true));

        for (int i = 0; i < 100; i++) {
            db.get(("key" + i).getBytes());
        }

        long blockCacheHits = statistics.getTickerCount(TickerType.BLOCK_CACHE_HIT);
        long blockCacheMisses = statistics.getTickerCount(TickerType.BLOCK_CACHE_MISS);
        long bytesWritten = statistics.getTickerCount(TickerType.BYTES_WRITTEN);
        long bytesRead = statistics.getTickerCount(TickerType.BYTES_READ);
        long writes = statistics.getTickerCount(TickerType.NUMBER_KEYS_WRITTEN);
        long reads = statistics.getTickerCount(TickerType.NUMBER_KEYS_READ);

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

    @Test
    void shouldPrintDetailedStats() throws RocksDBException {
        Options options = new Options()
            .setCreateIfMissing(true)
            .setStatistics(new Statistics());

        RocksDB db = RocksDB.open(options, tempDir.toString());

        for (int i = 0; i < 1000; i++) {
            db.put(("key" + i).getBytes(), new byte[1024]);
        }

        db.flush(new FlushOptions().setWaitForFlush(true));

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String stats = db.getProperty("rocksdb.stats");
        assertThat(stats).isNotNull();

        String tableProperties = db.getProperty("rocksdb.aggregated-table-properties");
        assertThat(tableProperties).isNotNull();

        db.close();
        options.close();
    }

    @Test
    void shouldMeasureLatencyHistograms() throws RocksDBException {
        Statistics statistics = new Statistics();

        Options options = new Options()
            .setCreateIfMissing(true)
            .setStatistics(statistics);

        RocksDB db = RocksDB.open(options, tempDir.toString());

        for (int i = 0; i < 500; i++) {
            db.put(("key" + i).getBytes(), ("value" + i).getBytes());
        }
        db.flush(new FlushOptions().setWaitForFlush(true));

        for (int i = 0; i < 500; i++) {
            db.get(("key" + i).getBytes());
        }

        HistogramData getHistogram = statistics.getHistogramData(HistogramType.DB_GET);
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

    @Test
    void shouldMonitorLevelStats() throws RocksDBException {
        Options options = new Options()
            .setCreateIfMissing(true)
            .setWriteBufferSize(1 * 1024 * 1024)
            .setTargetFileSizeBase(1 * 1024 * 1024);

        RocksDB db = RocksDB.open(options, tempDir.toString());

        byte[] value = new byte[1024 * 1024];
        for (int i = 0; i < 100; i++) {
            db.put(("key" + String.format("%04d", i)).getBytes(), value);
        }

        db.flush(new FlushOptions().setWaitForFlush(true));

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (int level = 0; level <= 6; level++) {
            String numFiles = db.getProperty("rocksdb.num-files-at-level" + level);
            int fileCount = Integer.parseInt(numFiles);
            if (fileCount > 0) {
                System.out.println("Level " + level + ": " + numFiles + " files");
            }
        }

        String liveSstSize = db.getProperty("rocksdb.live-sst-files-size");
        String totalSstSize = db.getProperty("rocksdb.total-sst-files-size");
        System.out.println("Live SST size: " + liveSstSize);
        System.out.println("Total SST size: " + totalSstSize);

        db.close();
        options.close();
    }
}
