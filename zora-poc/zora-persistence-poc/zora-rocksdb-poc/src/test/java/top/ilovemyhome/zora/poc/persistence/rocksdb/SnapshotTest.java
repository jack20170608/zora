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
 * Snapshot consistent read tests.
 */
class SnapshotTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldReadConsistentSnapshot() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        db.put("key".getBytes(), "v1".getBytes());

        Snapshot snapshot = db.getSnapshot();

        db.put("key".getBytes(), "v2".getBytes());

        try (ReadOptions readOptions = new ReadOptions().setSnapshot(snapshot)) {
            byte[] value = db.get(readOptions, "key".getBytes());
            assertThat(new String(value)).isEqualTo("v1");
        }

        byte[] latestValue = db.get("key".getBytes());
        assertThat(new String(latestValue)).isEqualTo("v2");

        snapshot.close();
        db.close();
        options.close();
    }

    @Test
    void shouldIterateWithSnapshot() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        db.put("a".getBytes(), "1".getBytes());
        db.put("b".getBytes(), "2".getBytes());

        Snapshot snapshot = db.getSnapshot();

        db.put("c".getBytes(), "3".getBytes());
        db.put("d".getBytes(), "4".getBytes());

        List<String> keys = new ArrayList<>();
        try (ReadOptions readOptions = new ReadOptions().setSnapshot(snapshot);
             RocksIterator it = db.newIterator(readOptions)) {

            it.seekToFirst();
            while (it.isValid()) {
                keys.add(new String(it.key()));
                it.next();
            }
        }

        assertThat(keys).containsExactly("a", "b");

        snapshot.close();
        db.close();
        options.close();
    }

    @Test
    void shouldMaintainConsistencyUnderConcurrentWrites() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        for (int i = 0; i < 10; i++) {
            db.put(("key" + i).getBytes(), ("v" + i).getBytes());
        }

        Snapshot snapshot = db.getSnapshot();

        for (int i = 0; i < 10; i++) {
            db.put(("key" + i).getBytes(), ("new" + i).getBytes());
        }

        try (ReadOptions readOptions = new ReadOptions().setSnapshot(snapshot)) {
            for (int i = 0; i < 10; i++) {
                byte[] value = db.get(readOptions, ("key" + i).getBytes());
                assertThat(new String(value)).isEqualTo("v" + i);
            }
        }

        for (int i = 0; i < 10; i++) {
            byte[] value = db.get(("key" + i).getBytes());
            assertThat(new String(value)).isEqualTo("new" + i);
        }

        snapshot.close();
        db.close();
        options.close();
    }
}
