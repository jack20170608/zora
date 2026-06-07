package top.ilovemyhome.zora.rocksdb.graph.store;

import static top.ilovemyhome.zora.rocksdb.graph.store.TestSupport.openTestStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the explicit {@link GraphTxn} API.
 * <p>The "marker accumulation" scenarios verify that read-modify-write
 * sequences run safely under transactions where the simpler one-shot
 * {@link GraphStore#addVertex} flavour would silently lose updates.
 */
class GraphTxnTest {

    private static final int PERSON_TYPE = 1;

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    // ========================== Lifecycle ==========================

    @Test
    void shouldPersistChangesAfterCommit() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            try (GraphTxn t = store.beginTransaction()) {
                t.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
                t.commit();
            }
            assertThat(store.getVertex(PERSON_TYPE, 1L).getProperty("name")).isEqualTo("Alice");
        }
    }

    @Test
    void shouldDiscardChangesAfterRollback() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            try (GraphTxn t = store.beginTransaction()) {
                t.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
                t.rollback();
            }
            assertThat(store.getVertex(PERSON_TYPE, 1L)).isNull();
        }
    }

    @Test
    void shouldImplicitlyRollbackOnCloseWithoutCommit() throws RocksDBException {
        // No explicit commit / rollback - close() must wipe pending writes.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            try (GraphTxn t = store.beginTransaction()) {
                t.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
                // Intentionally NO commit().
            }
            assertThat(store.getVertex(PERSON_TYPE, 1L)).isNull();
        }
    }

    @Test
    void shouldRejectOperationsAfterCommit() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            try (GraphTxn t = store.beginTransaction()) {
                t.commit();
                assertThatThrownBy(() -> t.addVertex(new Vertex(1L, PERSON_TYPE)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COMMITTED");
            }
        }
    }

    @Test
    void shouldRejectOperationsAfterRollback() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            try (GraphTxn t = store.beginTransaction()) {
                t.rollback();
                assertThatThrownBy(() -> t.getVertex(PERSON_TYPE, 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ROLLED_BACK");
            }
        }
    }

    // ========================== Read-your-own-writes ==========================

    @Test
    void shouldSeeOwnPendingWritesInsideTransaction() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            try (GraphTxn t = store.beginTransaction()) {
                t.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));

                // Inside the txn: see our own write before commit.
                Vertex pending = t.getVertex(PERSON_TYPE, 1L);
                assertThat(pending).isNotNull();
                assertThat(pending.getProperty("name")).isEqualTo("Alice");

                t.commit();
            }
        }
    }

    @Test
    void shouldHideUncommittedWritesFromOutsideReaders() throws Exception {
        // A pending write held inside one txn must NOT be visible to a
        // non-transactional GraphStore.getVertex on another thread until
        // the txn commits.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            try (GraphTxn t = store.beginTransaction()) {
                t.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));

                // Read on the main thread via the non-transactional path:
                // not committed yet -> null.
                assertThat(store.getVertex(PERSON_TYPE, 1L)).isNull();

                t.commit();
            }
            // After commit: visible.
            assertThat(store.getVertex(PERSON_TYPE, 1L)).isNotNull();
        }
    }

    // ========================== Read-modify-write (the headline use case) ==========================

    @Test
    void shouldAccumulateMarkersConcurrentlyInsideExplicitTransactions() throws Exception {
        // The scenario that motivated GraphTxn: many threads each want to
        // append a unique marker to the same vertex without losing any.
        // Each thread runs its read+merge+write inside its OWN GraphTxn,
        // so the pessimistic lock taken by t.getVertex serialises the
        // critical section across threads.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));

            int threadCount = 8;
            int iterPerThread = 25;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int t = 0; t < threadCount; t++) {
                    final int threadIdx = t;
                    futures.add(pool.submit(() -> {
                        for (int i = 0; i < iterPerThread; i++) {
                            try (GraphTxn txn = store.beginTransaction()) {
                                Vertex current = txn.getVertex(PERSON_TYPE, 1L);
                                Vertex updated = current.withProperty(
                                    "marker_" + threadIdx + "_" + i, true);
                                txn.addVertex(updated);
                                txn.commit();
                            } catch (RocksDBException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return null;
                    }));
                }
                for (var f : futures) f.get();
            } finally {
                pool.shutdownNow();
            }

            Vertex finalState = store.getVertex(PERSON_TYPE, 1L);
            long markerCount = finalState.getProperties().keySet().stream()
                .filter(k -> k.startsWith("marker_")).count();
            assertThat(markerCount).isEqualTo(threadCount * iterPerThread);
            assertThat(finalState.getProperty("name")).isEqualTo("Alice");
        }
    }

    // ========================== Multi-operation atomicity ==========================

    @Test
    void shouldRollbackMultiVertexBatchAtomically() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            try (GraphTxn t = store.beginTransaction()) {
                t.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
                t.addVertex(new Vertex(2L, PERSON_TYPE).withProperty("name", "Bob"));
                t.addVertex(new Vertex(3L, PERSON_TYPE).withProperty("name", "Charlie"));
                t.rollback();
            }
            assertThat(store.getVertex(PERSON_TYPE, 1L)).isNull();
            assertThat(store.getVertex(PERSON_TYPE, 2L)).isNull();
            assertThat(store.getVertex(PERSON_TYPE, 3L)).isNull();
        }
    }

    @Test
    void shouldExposePendingIndexEntryAfterCommit() throws RocksDBException {
        // findVerticesByProperty is intentionally NOT on GraphTxn (lock-free
        // path), so an index lookup BEFORE commit must miss, and AFTER
        // commit it must hit.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            try (GraphTxn t = store.beginTransaction()) {
                t.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("city", "BJ"));
                // Pre-commit: outside-the-txn index query sees nothing.
                assertThat(store.findVerticesByProperty(PERSON_TYPE, "city", "BJ")).isEmpty();
                t.commit();
            }
            // Post-commit: visible.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "city", "BJ"))
                .extracting(Vertex::getId).containsExactly(1L);
        }
    }
}
