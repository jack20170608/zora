package top.ilovemyhome.zora.rocksdb.graph.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.rocksdb.graph.codec.ValueCodec;
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Component-level micro-benchmarks. Each test isolates one cost so we can
 * decompose "where does the 83 us / op of addVertex actually go".
 */
class ComponentCostBenchmarkTest {

    private static final Logger LOG = LoggerFactory.getLogger(ComponentCostBenchmarkTest.class);
    private static final int N = 10_000;

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    // ========================== Jackson encode/decode in isolation ==========================

    @Test
    void jacksonEncodeDecodeNarrow() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Vertex narrow = new Vertex(1L, 1)
            .withProperty("name", "Alice").withProperty("age", 30);
        byte[] encoded = ValueCodec.encodeVertex(narrow);

        // warm
        for (int i = 0; i < 1000; i++) ValueCodec.encodeVertex(narrow);

        long t0 = System.nanoTime();
        for (int i = 0; i < N; i++) ValueCodec.encodeVertex(narrow);
        report("encode narrow (2 props)", t0);

        t0 = System.nanoTime();
        for (int i = 0; i < N; i++) ValueCodec.decodeVertex(encoded);
        report("decode narrow (2 props)", t0);
    }

    @Test
    void jacksonEncodeDecodeWide() throws Exception {
        Map<String, Object> props = new HashMap<>();
        for (int i = 0; i < 50; i++) props.put("prop_" + i, "value_" + i);
        Vertex wide = new Vertex(1L, 1, props);
        byte[] encoded = ValueCodec.encodeVertex(wide);

        for (int i = 0; i < 1000; i++) ValueCodec.encodeVertex(wide);

        long t0 = System.nanoTime();
        for (int i = 0; i < N; i++) ValueCodec.encodeVertex(wide);
        report("encode wide (50 props)", t0);

        t0 = System.nanoTime();
        for (int i = 0; i < N; i++) ValueCodec.decodeVertex(encoded);
        report("decode wide (50 props)", t0);
    }

    // ========================== Raw RocksDB.put (no transaction) ==========================

    @Test
    void rawRocksDbPut() throws Exception {
        try (Options options = new Options().setCreateIfMissing(true);
             RocksDB db = RocksDB.open(options, tempDir.resolve("raw").toString());
             WriteOptions wo = new WriteOptions().setSync(false)) {
            byte[] key = "K".getBytes(StandardCharsets.UTF_8);
            byte[] value = "V".getBytes(StandardCharsets.UTF_8);

            for (int i = 0; i < 1000; i++) db.put(wo, key, value);

            long t0 = System.nanoTime();
            for (int i = 0; i < N; i++) db.put(wo, key, value);
            report("raw RocksDB.put (1-byte K/V, sync=false)", t0);
        }
    }

    // ========================== TransactionDB: begin + commit ONLY ==========================

    @Test
    void txnBeginCommitOnly() throws Exception {
        try (DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
             TransactionDBOptions tdbOptions = new TransactionDBOptions();
             TransactionDB db = TransactionDB.open(dbOptions, tdbOptions,
                 tempDir.resolve("txn0").toString(),
                 java.util.List.of(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY)),
                 new java.util.ArrayList<>());
             WriteOptions wo = new WriteOptions().setSync(false);
             TransactionOptions to = new TransactionOptions().setDeadlockDetect(true)) {

            for (int i = 0; i < 1000; i++) {
                try (Transaction t = db.beginTransaction(wo, to)) { t.commit(); }
            }

            long t0 = System.nanoTime();
            for (int i = 0; i < N; i++) {
                try (Transaction t = db.beginTransaction(wo, to)) { t.commit(); }
            }
            report("Transaction: begin+commit ONLY", t0);
        }
    }

    // ========================== TransactionDB: begin + getForUpdate + commit ==========================

    @Test
    void txnGetForUpdateOnly() throws Exception {
        try (DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
             TransactionDBOptions tdbOptions = new TransactionDBOptions();
             TransactionDB db = TransactionDB.open(dbOptions, tdbOptions,
                 tempDir.resolve("txn1").toString(),
                 java.util.List.of(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY)),
                 new java.util.ArrayList<>());
             WriteOptions wo = new WriteOptions().setSync(false);
             ReadOptions ro = new ReadOptions();
             TransactionOptions to = new TransactionOptions().setDeadlockDetect(true)) {

            byte[] key = "K".getBytes(StandardCharsets.UTF_8);
            byte[] value = "V".getBytes(StandardCharsets.UTF_8);
            db.put(wo, key, value);   // populate

            for (int i = 0; i < 1000; i++) {
                try (Transaction t = db.beginTransaction(wo, to)) {
                    t.getForUpdate(ro, key, true);
                    t.commit();
                }
            }

            long t0 = System.nanoTime();
            for (int i = 0; i < N; i++) {
                try (Transaction t = db.beginTransaction(wo, to)) {
                    t.getForUpdate(ro, key, true);
                    t.commit();
                }
            }
            report("Transaction: begin+getForUpdate+commit", t0);
        }
    }

    // ========================== TransactionDB: begin + getForUpdate + put + commit ==========================

    @Test
    void txnFullPutCycle() throws Exception {
        try (DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
             TransactionDBOptions tdbOptions = new TransactionDBOptions();
             TransactionDB db = TransactionDB.open(dbOptions, tdbOptions,
                 tempDir.resolve("txn2").toString(),
                 java.util.List.of(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY)),
                 new java.util.ArrayList<>());
             WriteOptions wo = new WriteOptions().setSync(false);
             ReadOptions ro = new ReadOptions();
             TransactionOptions to = new TransactionOptions().setDeadlockDetect(true)) {

            byte[] key = "K".getBytes(StandardCharsets.UTF_8);
            byte[] value = "V".getBytes(StandardCharsets.UTF_8);
            db.put(wo, key, value);

            for (int i = 0; i < 1000; i++) {
                try (Transaction t = db.beginTransaction(wo, to)) {
                    t.getForUpdate(ro, key, true);
                    t.put(key, value);
                    t.commit();
                }
            }

            long t0 = System.nanoTime();
            for (int i = 0; i < N; i++) {
                try (Transaction t = db.beginTransaction(wo, to)) {
                    t.getForUpdate(ro, key, true);
                    t.put(key, value);
                    t.commit();
                }
            }
            report("Transaction: begin+getForUpdate+put+commit (single key, no JSON)", t0);
        }
    }

    private static void report(String label, long t0) {
        long elapsedNs = System.nanoTime() - t0;
        LOG.info("{}: {} iters | {} us/op",
            label, N, String.format("%.2f", elapsedNs / 1000.0 / N));
    }
}
