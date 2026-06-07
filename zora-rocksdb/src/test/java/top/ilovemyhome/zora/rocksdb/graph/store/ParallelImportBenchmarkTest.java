package top.ilovemyhome.zora.rocksdb.graph.store;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;

import java.nio.file.Path;

/**
 * Multi-threaded import throughput vs single-threaded baseline.
 */
class ParallelImportBenchmarkTest {

    private static final Logger LOG = LoggerFactory.getLogger(ParallelImportBenchmarkTest.class);
    private static final int PERSON_TYPE = 1;
    private static final int TOTAL = 100_000;
    private static final int BATCH = 500;

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void benchmarkParallelImportScaling() throws Exception {
        int[] workerCounts = {1, 2, 4, 8};
        int cores = Runtime.getRuntime().availableProcessors();
        LOG.info("Machine has {} available processors", cores);

        for (int workers : workerCounts) {
            try (GraphStore store = new GraphStore(tempDir.resolve("w" + workers).toString())) {
                long start = System.nanoTime();
                try (var importer = ParallelGraphImporter.vertexImporter(store, workers, BATCH)) {
                    for (long id = 1; id <= TOTAL; id++) {
                        importer.submit(new Vertex(id, PERSON_TYPE)
                            .withProperty("name", "User" + id)
                            .withProperty("age", (int) (id % 100)));
                    }
                }   // close blocks until everything drained
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                double opsPerSec = TOTAL * 1000.0 / Math.max(1, elapsedMs);
                LOG.info("Parallel import workers={} batch={}: {} vertices in {} ms | {} ops/sec",
                    workers, BATCH, TOTAL, elapsedMs, String.format("%.0f", opsPerSec));
            }
        }
    }
}
