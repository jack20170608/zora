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
 * RocksIterator forward/backward iteration and range scanning.
 */
class IteratorTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    private RocksDB prepareDb() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        db.put("a".getBytes(), "1".getBytes());
        db.put("b".getBytes(), "2".getBytes());
        db.put("c".getBytes(), "3".getBytes());
        db.put("d".getBytes(), "4".getBytes());

        return db;
    }

    @Test
    void shouldIterateForward() throws RocksDBException {
        RocksDB db = prepareDb();

        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();

        try (RocksIterator it = db.newIterator()) {
            it.seekToFirst();
            while (it.isValid()) {
                keys.add(new String(it.key()));
                values.add(new String(it.value()));
                it.next();
            }
        }

        assertThat(keys).containsExactly("a", "b", "c", "d");
        assertThat(values).containsExactly("1", "2", "3", "4");

        db.close();
    }

    @Test
    void shouldIterateBackward() throws RocksDBException {
        RocksDB db = prepareDb();

        List<String> keys = new ArrayList<>();

        try (RocksIterator it = db.newIterator()) {
            it.seekToLast();
            while (it.isValid()) {
                keys.add(new String(it.key()));
                it.prev();
            }
        }

        assertThat(keys).containsExactly("d", "c", "b", "a");

        db.close();
    }

    @Test
    void shouldSeekToKey() throws RocksDBException {
        RocksDB db = prepareDb();

        try (RocksIterator it = db.newIterator()) {
            it.seek("b".getBytes());
            assertThat(it.isValid()).isTrue();
            assertThat(new String(it.key())).isEqualTo("b");
            assertThat(new String(it.value())).isEqualTo("2");
        }

        db.close();
    }

    @Test
    void shouldSeekToNextExistingKey() throws RocksDBException {
        RocksDB db = prepareDb();

        try (RocksIterator it = db.newIterator()) {
            it.seek("bb".getBytes());
            assertThat(it.isValid()).isTrue();
            assertThat(new String(it.key())).isEqualTo("c");
        }

        db.close();
    }

    @Test
    void shouldScanRange() throws RocksDBException {
        RocksDB db = prepareDb();

        List<String> result = new ArrayList<>();

        try (RocksIterator it = db.newIterator()) {
            it.seek("b".getBytes());
            while (it.isValid()) {
                String key = new String(it.key());
                if (key.compareTo("d") > 0) {
                    break;
                }
                result.add(key + "=" + new String(it.value()));
                it.next();
            }
        }

        assertThat(result).containsExactly("b=2", "c=3", "d=4");

        db.close();
    }

    @Test
    void shouldScanByPrefix() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        db.put("user:alice".getBytes(), "data1".getBytes());
        db.put("user:bob".getBytes(), "data2".getBytes());
        db.put("user:charlie".getBytes(), "data3".getBytes());
        db.put("order:o1".getBytes(), "order1".getBytes());
        db.put("order:o2".getBytes(), "order2".getBytes());

        List<String> userKeys = new ArrayList<>();

        try (RocksIterator it = db.newIterator()) {
            it.seek("user:".getBytes());
            while (it.isValid()) {
                String key = new String(it.key());
                if (!key.startsWith("user:")) {
                    break;
                }
                userKeys.add(key);
                it.next();
            }
        }

        assertThat(userKeys).containsExactly("user:alice", "user:bob", "user:charlie");

        db.close();
        options.close();
    }

    @Test
    void shouldIterateOverColumnFamily() throws RocksDBException {
        List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
        cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

        try (DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {

            ColumnFamilyHandle usersCf = db.createColumnFamily(
                new ColumnFamilyDescriptor("users".getBytes())
            );

            db.put(usersCf, "u1".getBytes(), "alice".getBytes());
            db.put(usersCf, "u2".getBytes(), "bob".getBytes());

            List<String> keys = new ArrayList<>();
            try (RocksIterator it = db.newIterator(usersCf)) {
                it.seekToFirst();
                while (it.isValid()) {
                    keys.add(new String(it.key()));
                    it.next();
                }
            }

            assertThat(keys).containsExactly("u1", "u2");

            usersCf.close();
        }
    }
}
