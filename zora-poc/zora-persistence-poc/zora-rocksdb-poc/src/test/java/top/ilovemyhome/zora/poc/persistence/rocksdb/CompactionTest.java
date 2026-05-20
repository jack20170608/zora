package top.ilovemyhome.zora.poc.persistence.rocksdb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compaction style experiments and LSM-Tree observation.
 */
class CompactionTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldUseUniversalCompaction() throws RocksDBException {
        Options options = new Options()
            .setCreateIfMissing(true)
            .setCompactionStyle(CompactionStyle.UNIVERSAL)
            .setLevel0FileNumCompactionTrigger(2)
            .setMaxBytesForLevelBase(64 * 1024 * 1024);

        RocksDB db = RocksDB.open(options, tempDir.toString());

        byte[] value = new byte[1024 * 1024];
        for (int i = 0; i < 100; i++) {
            db.put(("key" + i).getBytes(), value);
        }

        db.flush(new FlushOptions().setWaitForFlush(true));

        String stats = db.getProperty("rocksdb.stats");
        assertThat(stats).isNotNull();

        db.close();
        options.close();
    }

    @Test
    void shouldObserveLevelFiles() throws RocksDBException {
        Options options = new Options()
            .setCreateIfMissing(true)
            .setWriteBufferSize(1 * 1024 * 1024)
            .setMaxWriteBufferNumber(2)
            .setLevel0FileNumCompactionTrigger(2)
            .setTargetFileSizeBase(1 * 1024 * 1024)
            .setMaxBytesForLevelBase(10 * 1024 * 1024);

        RocksDB db = RocksDB.open(options, tempDir.toString());

        byte[] value = new byte[100 * 1024];
        for (int i = 0; i < 500; i++) {
            db.put(("k" + String.format("%04d", i)).getBytes(), value);
        }

        db.flush(new FlushOptions().setWaitForFlush(true));

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (int level = 0; level <= 6; level++) {
            String prop = "rocksdb.num-files-at-level" + level;
            String count = db.getProperty(prop);
            System.out.println("Level " + level + ": " + count + " files");
        }

        db.close();
        options.close();
    }

    @Test
    void shouldUseDynamicLevelBytes() throws RocksDBException {
        Options options = new Options()
            .setCreateIfMissing(true)
            .setLevelCompactionDynamicLevelBytes(true)
            .setMaxBytesForLevelBase(64 * 1024 * 1024)
            .setMaxBytesForLevelMultiplier(10);

        RocksDB db = RocksDB.open(options, tempDir.toString());

        byte[] value = new byte[1024 * 1024];
        for (int i = 0; i < 200; i++) {
            db.put(("key" + i).getBytes(), value);
        }

        db.flush(new FlushOptions().setWaitForFlush(true));

        String baseLevel = db.getProperty("rocksdb.base-level");
        System.out.println("Base level: " + baseLevel);

        db.close();
        options.close();
    }

    @Test
    void shouldReadCompactionStats() throws RocksDBException {
        Options options = new Options()
            .setCreateIfMissing(true)
            .setStatistics(new Statistics());

        RocksDB db = RocksDB.open(options, tempDir.toString());

        byte[] value = new byte[1024 * 1024];
        for (int i = 0; i < 50; i++) {
            db.put(("key" + i).getBytes(), value);
        }

        db.flush(new FlushOptions().setWaitForFlush(true));

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String stats = db.getProperty("rocksdb.stats");
        assertThat(stats).isNotNull();
        System.out.println(stats);

        db.close();
        options.close();
    }
}
