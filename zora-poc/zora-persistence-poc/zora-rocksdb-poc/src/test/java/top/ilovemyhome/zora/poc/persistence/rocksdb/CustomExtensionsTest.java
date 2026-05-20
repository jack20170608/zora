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
 * Merge operator and builtin comparator tests.
 */
class CustomExtensionsTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldUseStringAppendMergeOperator() throws RocksDBException {
        StringAppendOperator mergeOperator = new StringAppendOperator(",");

        ColumnFamilyOptions cfOptions = new ColumnFamilyOptions()
            .setMergeOperator(mergeOperator);

        DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);

        List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
        cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

        try (RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {
            byte[] key = "log".getBytes();

            db.put(key, "init".getBytes());
            db.merge(key, "event1".getBytes());
            db.merge(key, "event2".getBytes());
            db.merge(key, "event3".getBytes());

            byte[] result = db.get(key);
            String merged = new String(result);

            assertThat(merged).contains("init");
            assertThat(merged).contains("event1");
            assertThat(merged).contains("event2");
            assertThat(merged).contains("event3");
        }

        cfOptions.close();
        dbOptions.close();
    }

    @Test
    void shouldUseBuiltinComparator() throws RocksDBException {
        ColumnFamilyOptions cfOptions = new ColumnFamilyOptions()
            .setComparator(BuiltinComparator.BYTEWISE_COMPARATOR);

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
                it.seekToFirst();
                while (it.isValid()) {
                    keys.add(new String(it.key()));
                    it.next();
                }
            }

            assertThat(keys).containsExactly("a", "b", "c");
        }

        cfOptions.close();
        dbOptions.close();
    }
}
