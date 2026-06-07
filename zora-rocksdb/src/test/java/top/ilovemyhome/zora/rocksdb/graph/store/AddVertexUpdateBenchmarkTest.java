package top.ilovemyhome.zora.rocksdb.graph.store;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Micro-benchmarks specifically for "addVertex on an existing vertex" - the
 * scenario where every addVertex pays the full diff cost (decode old + diff
 * + encode new + write index churn). Also measures the impact of the
 * no-op short-circuit and the batch API.
 */
class AddVertexUpdateBenchmarkTest {

    private static final Logger LOG = LoggerFactory.getLogger(AddVertexUpdateBenchmarkTest.class);
    private static final int PERSON_TYPE = 1;

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    /**
     * Same vertex updated N times. Each call goes through:
     *   getForUpdate -> JSON decode old -> diff -> JSON encode new ->
     *   delete + put per-changed-prop -> commit.
     */
    @Test
    void benchmarkSingleVertexRepeatedUpdate() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));

            int iterations = 10_000;
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                store.addVertex(new Vertex(1L, PERSON_TYPE)
                    .withProperty("name", "Alice")
                    .withProperty("counter", i));    // one property actually changes
            }
            long elapsedNs = System.nanoTime() - start;
            double perOpUs = elapsedNs / 1000.0 / iterations;
            LOG.info("Single-vertex update: {} iters in {} ms | per-op {} us",
                iterations, elapsedNs / 1_000_000, String.format("%.2f", perOpUs));
        }
    }

    /**
     * Wide-vertex update: same as above but vertex carries 50 properties and
     * only one of them changes per iteration. Exposes the cost of JSON
     * encode/decode the whole map every time.
     */
    @Test
    void benchmarkWideVertexUpdate() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            Map<String, Object> base = new HashMap<>();
            for (int i = 0; i < 50; i++) {
                base.put("prop_" + i, "value_" + i);
            }
            store.addVertex(new Vertex(1L, PERSON_TYPE, base));

            int iterations = 10_000;
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                Map<String, Object> props = new HashMap<>(base);
                props.put("counter", i);            // one mutating field
                store.addVertex(new Vertex(1L, PERSON_TYPE, props));
            }
            long elapsedNs = System.nanoTime() - start;
            double perOpUs = elapsedNs / 1000.0 / iterations;
            LOG.info("Wide-vertex (50 props) update: {} iters in {} ms | per-op {} us",
                iterations, elapsedNs / 1_000_000, String.format("%.2f", perOpUs));
        }
    }

    /**
     * No-op write: caller submits a vertex whose properties match what's
     * already stored. With the short-circuit optimisation we skip the main
     * store put, JSON encode, and all index churn. Compare against
     * {@link #benchmarkSingleVertexRepeatedUpdate()}: the only work left
     * is open-txn -> getForUpdate -> decode old -> equals -> commit.
     */
    @Test
    void benchmarkNoOpUpdateShortCircuit() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            Vertex same = new Vertex(1L, PERSON_TYPE)
                .withProperty("name", "Alice")
                .withProperty("age", 30);
            store.addVertex(same);

            int iterations = 10_000;
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                // Always the same content - short-circuit should kick in.
                store.addVertex(new Vertex(1L, PERSON_TYPE)
                    .withProperty("name", "Alice")
                    .withProperty("age", 30));
            }
            long elapsedNs = System.nanoTime() - start;
            double perOpUs = elapsedNs / 1000.0 / iterations;
            LOG.info("No-op update (short-circuit): {} iters in {} ms | per-op {} us",
                iterations, elapsedNs / 1_000_000, String.format("%.2f", perOpUs));
        }
    }

    /**
     * Bulk insert: how much do we save by amortising commit() over a batch.
     * Compare against {@link GraphStoreBenchmarkTest#benchmarkVertexWriteThroughput()}
     * which runs one transaction per vertex.
     */
    @Test
    void benchmarkBulkVertexInsert() throws RocksDBException {
        int total = 10_000;
        int[] batchSizes = {1, 100, 1000, 5000};

        for (int batchSize : batchSizes) {
            try (GraphStore store = new GraphStore(tempDir.resolve("b" + batchSize).toString())) {
                long start = System.nanoTime();
                long nextId = 1;
                for (int written = 0; written < total; written += batchSize) {
                    int n = Math.min(batchSize, total - written);
                    List<Vertex> batch = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        batch.add(new Vertex(nextId++, PERSON_TYPE)
                            .withProperty("name", "User" + (nextId - 1))
                            .withProperty("age", (int) ((nextId - 1) % 100)));
                    }
                    store.addVertices(batch);
                }
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                double opsPerSec = total * 1000.0 / Math.max(1, elapsedMs);
                LOG.info("Bulk insert batch={}: {} vertices in {} ms | {} ops/sec",
                    batchSize, total, elapsedMs, String.format("%.0f", opsPerSec));
            }
        }
    }

    /**
     * Head-to-head: same logical update ("change one field of a wide vertex")
     * via the full addVertex path vs the new updateVertexProperty path.
     *
     * <p>addVertex requires the caller to build a fresh 50-key map and pays
     * a diff loop over both old and new property maps. updateVertexProperty
     * only walks the changes map (1 entry), spares the caller from
     * rebuilding the full vertex, and only touches the single changed
     * index entry.
     */
    @Test
    void benchmarkPartialVsFullUpdateOnWideVertex() throws RocksDBException {
        // Run each variant in its own DB to avoid cross-pollution.
        int iterations = 10_000;

        // --- Variant A: full addVertex ---
        try (GraphStore store = new GraphStore(tempDir.resolve("full").toString())) {
            Map<String, Object> base = new HashMap<>();
            for (int i = 0; i < 50; i++) base.put("prop_" + i, "value_" + i);
            store.addVertex(new Vertex(1L, PERSON_TYPE, base));

            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                Map<String, Object> props = new HashMap<>(base);
                props.put("counter", i);
                store.addVertex(new Vertex(1L, PERSON_TYPE, props));
            }
            long elapsedNs = System.nanoTime() - start;
            LOG.info("addVertex (wide-vertex, 50 props, full overwrite): {} iters | per-op {} us",
                iterations, String.format("%.2f", elapsedNs / 1000.0 / iterations));
        }

        // --- Variant B: updateVertexProperty ---
        try (GraphStore store = new GraphStore(tempDir.resolve("partial").toString())) {
            Map<String, Object> base = new HashMap<>();
            for (int i = 0; i < 50; i++) base.put("prop_" + i, "value_" + i);
            store.addVertex(new Vertex(1L, PERSON_TYPE, base));

            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                store.updateVertexProperty(PERSON_TYPE, 1L, "counter", i);
            }
            long elapsedNs = System.nanoTime() - start;
            LOG.info("updateVertexProperty (wide-vertex, 50 props, single field): {} iters | per-op {} us",
                iterations, String.format("%.2f", elapsedNs / 1000.0 / iterations));
        }
    }
}


