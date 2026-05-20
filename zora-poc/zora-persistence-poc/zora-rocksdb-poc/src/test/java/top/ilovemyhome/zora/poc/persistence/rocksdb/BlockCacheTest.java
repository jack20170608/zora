package top.ilovemyhome.zora.poc.persistence.rocksdb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Block Cache and Bloom Filter performance tests.
 */
class BlockCacheTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldUseBlockCache() throws RocksDBException {
        Cache cache = new LRUCache(64 * 1024 * 1024);

        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
            .setBlockCache(cache)
            .setBlockSize(16 * 1024);

        Options options = new Options()
            .setCreateIfMissing(true)
            .setTableFormatConfig(tableConfig)
            .setStatistics(new Statistics());

        RocksDB db = RocksDB.open(options, tempDir.toString());

        for (int i = 0; i < 1000; i++) {
            db.put(("key" + i).getBytes(), ("value" + i).getBytes());
        }
        db.flush(new FlushOptions().setWaitForFlush(true));

        // Cold read
        for (int i = 0; i < 1000; i++) {
            db.get(("key" + i).getBytes());
        }

        long coldMisses = options.statistics().getTickerCount(TickerType.BLOCK_CACHE_MISS);

        // Warm read
        for (int i = 0; i < 1000; i++) {
            db.get(("key" + i).getBytes());
        }

        long warmHits = options.statistics().getTickerCount(TickerType.BLOCK_CACHE_HIT);

        System.out.println("Block cache misses after cold read: " + coldMisses);
        System.out.println("Block cache hits after warm read: " + warmHits);

        assertThat(coldMisses).isGreaterThan(0);
        assertThat(warmHits).isGreaterThan(0);

        db.close();
        options.close();
        cache.close();
    }

    @Test
    void shouldUseBloomFilter() throws RocksDBException {
        BloomFilter bloomFilter = new BloomFilter(10);

        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
            .setFilterPolicy(bloomFilter);

        Options options = new Options()
            .setCreateIfMissing(true)
            .setTableFormatConfig(tableConfig);

        RocksDB db = RocksDB.open(options, tempDir.toString());

        for (int i = 0; i < 1000; i++) {
            db.put(("key" + i).getBytes(), ("value" + i).getBytes());
        }
        db.flush(new FlushOptions().setWaitForFlush(true));

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

    @Test
    void shouldMonitorCacheHitRate() throws RocksDBException {
        Cache cache = new LRUCache(64 * 1024 * 1024);

        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
            .setBlockCache(cache);

        Options options = new Options()
            .setCreateIfMissing(true)
            .setTableFormatConfig(tableConfig)
            .setStatistics(new Statistics());

        RocksDB db = RocksDB.open(options, tempDir.toString());

        for (int i = 0; i < 500; i++) {
            db.put(("key" + i).getBytes(), ("value" + i).getBytes());
        }
        db.flush(new FlushOptions().setWaitForFlush(true));

        for (int i = 0; i < 500; i++) {
            db.get(("key" + i).getBytes());
        }

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

    @Test
    void shouldConfigureForProduction() throws RocksDBException {
        Cache cache = new LRUCache(512 * 1024 * 1024);
        BloomFilter bloomFilter = new BloomFilter(10);

        BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
            .setBlockCache(cache)
            .setBlockSize(16 * 1024)
            .setFilterPolicy(bloomFilter)
            .setCacheIndexAndFilterBlocks(true);

        ColumnFamilyOptions cfOptions = new ColumnFamilyOptions()
            .setTableFormatConfig(tableConfig);

        DBOptions dbOptions = new DBOptions()
            .setCreateIfMissing(true)
            .setStatistics(new Statistics());

        List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
        cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

        try (RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {
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
}
