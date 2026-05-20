package top.ilovemyhome.zora.poc.persistence.rocksdb;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic CRUD operations for RocksDB.
 */
class BasicOperationTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldOpenAndCloseDb() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        assertThat(db).isNotNull();

        db.close();
        options.close();
    }

    @Test
    void shouldPutGetAndDelete() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        byte[] key = "hello".getBytes();
        byte[] value = "world".getBytes();

        db.put(key, value);

        byte[] retrieved = db.get(key);
        assertThat(new String(retrieved)).isEqualTo("world");

        db.delete(key);

        byte[] afterDelete = db.get(key);
        assertThat(afterDelete).isNull();

        db.close();
        options.close();
    }

    @Test
    void shouldGetMultipleKeys() throws RocksDBException {
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

    @Test
    void shouldConfigureDbOptions() throws RocksDBException {
        Options options = new Options()
            .setCreateIfMissing(true)
            .setDbWriteBufferSize(64 * 1024 * 1024)
            .setMaxOpenFiles(1000)
            .setMaxBackgroundJobs(4);

        RocksDB db = RocksDB.open(options, tempDir.toString());
        assertThat(db).isNotNull();

        db.close();
        options.close();
    }
}
