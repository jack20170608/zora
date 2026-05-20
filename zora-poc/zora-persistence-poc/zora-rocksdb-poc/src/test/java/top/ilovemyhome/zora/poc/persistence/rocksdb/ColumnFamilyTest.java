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
 * Column family creation, listing, and multi-CF operations.
 */
class ColumnFamilyTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateAndUseColumnFamilies() throws RocksDBException {
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, tempDir.toString())) {
            db.close();
        }

        List<byte[]> cfNames = RocksDB.listColumnFamilies(new Options(), tempDir.toString());
        assertThat(cfNames).hasSize(1);
        assertThat(new String(cfNames.get(0))).isEqualTo("default");
    }

    @Test
    void shouldCreateNewColumnFamily() throws RocksDBException {
        List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
        cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

        try (DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {

            ColumnFamilyHandle defaultCf = cfHandles.get(0);

            ColumnFamilyDescriptor newCfDesc = new ColumnFamilyDescriptor("users".getBytes());
            ColumnFamilyHandle usersCf = db.createColumnFamily(newCfDesc);

            db.put(defaultCf, "id".getBytes(), "default-value".getBytes());
            db.put(usersCf, "id".getBytes(), "user-value".getBytes());

            assertThat(new String(db.get(defaultCf, "id".getBytes()))).isEqualTo("default-value");
            assertThat(new String(db.get(usersCf, "id".getBytes()))).isEqualTo("user-value");

            usersCf.close();
        }
    }

    @Test
    void shouldReopenWithExistingColumnFamilies() throws RocksDBException {
        String dbPath = tempDir.toString();

        // Phase 1: create DB and CFs
        {
            List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
            cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));

            List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

            try (DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);
                 RocksDB db = RocksDB.open(dbOptions, dbPath, cfDescriptors, cfHandles)) {

                ColumnFamilyHandle usersCf = db.createColumnFamily(
                    new ColumnFamilyDescriptor("users".getBytes())
                );
                ColumnFamilyHandle ordersCf = db.createColumnFamily(
                    new ColumnFamilyDescriptor("orders".getBytes())
                );

                db.put(usersCf, "u1".getBytes(), "alice".getBytes());
                db.put(ordersCf, "o1".getBytes(), "100".getBytes());

                usersCf.close();
                ordersCf.close();
            }
        }

        // Phase 2: reopen with all CFs listed
        {
            List<byte[]> cfNames = RocksDB.listColumnFamilies(new Options(), dbPath);

            List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
            for (byte[] name : cfNames) {
                cfDescriptors.add(new ColumnFamilyDescriptor(name));
            }

            List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

            try (DBOptions dbOptions = new DBOptions();
                 RocksDB db = RocksDB.open(dbOptions, dbPath, cfDescriptors, cfHandles)) {

                assertThat(cfHandles).hasSize(3);

                ColumnFamilyHandle usersCf = null;
                ColumnFamilyHandle ordersCf = null;
                for (ColumnFamilyHandle handle : cfHandles) {
                    String name = new String(handle.getName());
                    if ("users".equals(name)) usersCf = handle;
                    if ("orders".equals(name)) ordersCf = handle;
                }

                assertThat(usersCf).isNotNull();
                assertThat(ordersCf).isNotNull();

                assertThat(new String(db.get(usersCf, "u1".getBytes()))).isEqualTo("alice");
                assertThat(new String(db.get(ordersCf, "o1".getBytes()))).isEqualTo("100");
            }
        }
    }

    @Test
    void shouldDropColumnFamily() throws RocksDBException {
        List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
        cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

        try (DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {

            ColumnFamilyHandle tempCf = db.createColumnFamily(
                new ColumnFamilyDescriptor("temp".getBytes())
            );

            db.put(tempCf, "key".getBytes(), "value".getBytes());
            assertThat(new String(db.get(tempCf, "key".getBytes()))).isEqualTo("value");

            db.dropColumnFamily(tempCf);
            tempCf.close();

            List<byte[]> remaining = RocksDB.listColumnFamilies(new Options(), tempDir.toString());
            assertThat(remaining.stream().map(String::new).toList())
                .doesNotContain("temp");
        }
    }

    @Test
    void shouldUseDifferentOptionsPerCf() throws RocksDBException {
        // Phase 1: create DB and users CF
        {
            List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
            cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));

            List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

            try (DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);
                 RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {

                ColumnFamilyHandle usersCf = db.createColumnFamily(
                    new ColumnFamilyDescriptor("users".getBytes())
                );
                usersCf.close();
            }
        }

        // Phase 2: reopen with different options per CF
        List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();

        ColumnFamilyOptions defaultCfOpts = new ColumnFamilyOptions();
        cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, defaultCfOpts));

        ColumnFamilyOptions usersCfOpts = new ColumnFamilyOptions()
            .setWriteBufferSize(64 * 1024 * 1024);
        cfDescriptors.add(new ColumnFamilyDescriptor("users".getBytes(), usersCfOpts));

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

        try (DBOptions dbOptions = new DBOptions();
             RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {

            assertThat(cfHandles).hasSize(2);

            for (ColumnFamilyHandle handle : cfHandles) {
                handle.close();
            }
        }

        defaultCfOpts.close();
        usersCfOpts.close();
    }
}
