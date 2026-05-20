package top.ilovemyhome.zora.poc.persistence.rocksdb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WriteBatch atomic batching and performance tests.
 */
class WriteBatchTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldBatchPutAndDelete() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        try (WriteBatch batch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions()) {

            batch.put("key1".getBytes(), "value1".getBytes());
            batch.put("key2".getBytes(), "value2".getBytes());
            batch.put("key3".getBytes(), "value3".getBytes());
            batch.delete("key2".getBytes());

            db.write(writeOptions, batch);
        }

        assertThat(new String(db.get("key1".getBytes()))).isEqualTo("value1");
        assertThat(db.get("key2".getBytes())).isNull();
        assertThat(new String(db.get("key3".getBytes()))).isEqualTo("value3");

        db.close();
        options.close();
    }

    @Test
    void shouldBeAtomic() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        db.put("key1".getBytes(), "old".getBytes());

        // Batch created but not written
        try (WriteBatch batch = new WriteBatch()) {
            batch.put("key1".getBytes(), "new".getBytes());
            batch.put("key2".getBytes(), "value2".getBytes());
        }

        // Nothing should change
        assertThat(new String(db.get("key1".getBytes()))).isEqualTo("old");
        assertThat(db.get("key2".getBytes())).isNull();

        db.close();
        options.close();
    }

    @Test
    void shouldBeFasterThanIndividualPuts() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        int count = 1000;
        byte[] value = "x".repeat(100).getBytes();

        long start1 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            db.put(("key" + i).getBytes(), value);
        }
        long duration1 = System.currentTimeMillis() - start1;

        // Reset
        for (int i = 0; i < count; i++) {
            db.delete(("key" + i).getBytes());
        }

        long start2 = System.currentTimeMillis();
        try (WriteBatch batch = new WriteBatch();
             WriteOptions writeOptions = new WriteOptions()) {
            for (int i = 0; i < count; i++) {
                batch.put(("key" + i).getBytes(), value);
            }
            db.write(writeOptions, batch);
        }
        long duration2 = System.currentTimeMillis() - start2;

        System.out.println("Individual puts: " + duration1 + " ms");
        System.out.println("WriteBatch: " + duration2 + " ms");

        assertThat(duration2).isLessThan(duration1);

        db.close();
        options.close();
    }

    @Test
    void shouldControlSyncBehavior() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        try (WriteBatch batch = new WriteBatch()) {
            batch.put("key".getBytes(), "value".getBytes());

            try (WriteOptions async = new WriteOptions().setSync(false)) {
                db.write(async, batch);
            }

            try (WriteOptions sync = new WriteOptions().setSync(true)) {
                db.write(sync, batch);
            }

            try (WriteOptions noWal = new WriteOptions().setDisableWAL(true)) {
                db.write(noWal, batch);
            }
        }

        db.close();
        options.close();
    }
}
