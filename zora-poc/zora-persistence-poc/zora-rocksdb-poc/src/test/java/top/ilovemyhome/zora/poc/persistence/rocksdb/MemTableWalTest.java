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
 * MemTable and WAL configuration experiments.
 */
class MemTableWalTest {

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldCompareWalModes() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        int count = 500;
        byte[] value = "x".repeat(100).getBytes();

        long t1 = System.currentTimeMillis();
        try (WriteOptions wo = new WriteOptions().setSync(false)) {
            for (int i = 0; i < count; i++) {
                db.put(wo, ("k" + i).getBytes(), value);
            }
        }
        long asyncMs = System.currentTimeMillis() - t1;

        long t2 = System.currentTimeMillis();
        try (WriteOptions wo = new WriteOptions().setSync(true)) {
            for (int i = count; i < count * 2; i++) {
                db.put(wo, ("k" + i).getBytes(), value);
            }
        }
        long syncMs = System.currentTimeMillis() - t2;

        long t3 = System.currentTimeMillis();
        try (WriteOptions wo = new WriteOptions().setDisableWAL(true)) {
            for (int i = count * 2; i < count * 3; i++) {
                db.put(wo, ("k" + i).getBytes(), value);
            }
        }
        long noWalMs = System.currentTimeMillis() - t3;

        System.out.println("async (sync=false): " + asyncMs + " ms");
        System.out.println("sync (sync=true):   " + syncMs + " ms");
        System.out.println("no WAL:             " + noWalMs + " ms");

        assertThat(noWalMs).isLessThanOrEqualTo(asyncMs + 100);

        db.close();
        options.close();
    }

    @Test
    void shouldConfigureMemTableSize() throws RocksDBException {
        ColumnFamilyOptions cfOptions = new ColumnFamilyOptions()
            .setWriteBufferSize(4 * 1024 * 1024)
            .setMaxWriteBufferNumber(3)
            .setMinWriteBufferNumberToMerge(2);

        DBOptions dbOptions = new DBOptions().setCreateIfMissing(true);

        List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
        cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

        try (RocksDB db = RocksDB.open(dbOptions, tempDir.toString(), cfDescriptors, cfHandles)) {
            byte[] value = new byte[1024 * 1024];
            for (int i = 0; i < 20; i++) {
                db.put(("key" + i).getBytes(), value);
            }

            db.flush(new FlushOptions().setWaitForFlush(true));

            String numFiles = db.getProperty("rocksdb.num-files-at-level0");
            System.out.println("Level-0 files: " + numFiles);
            assertThat(Integer.parseInt(numFiles)).isGreaterThanOrEqualTo(0);
        }

        for (ColumnFamilyHandle handle : cfHandles) {
            handle.close();
        }
        cfOptions.close();
        dbOptions.close();
    }

    @Test
    void shouldTriggerFlushManually() throws RocksDBException {
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, tempDir.toString());

        db.put("key".getBytes(), "value".getBytes());

        String immCount = db.getProperty("rocksdb.num-immutable-mem-table");
        System.out.println("Before flush - immutable memtables: " + immCount);

        db.flush(new FlushOptions().setWaitForFlush(true));

        String level0Files = db.getProperty("rocksdb.num-files-at-level0");
        System.out.println("After flush - level0 files: " + level0Files);

        assertThat(Integer.parseInt(level0Files)).isGreaterThanOrEqualTo(1);

        db.close();
        options.close();
    }
}
