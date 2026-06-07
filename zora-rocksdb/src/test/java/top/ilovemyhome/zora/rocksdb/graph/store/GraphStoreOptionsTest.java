package top.ilovemyhome.zora.rocksdb.graph.store;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests covering {@link GraphStoreOptions} integration: lock-timeout
 * fail-fast and durable write toggle.
 */
class GraphStoreOptionsTest {

    private static final int PERSON_TYPE = 1;

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void defaultsShouldExposeReasonableValues() {
        GraphStoreOptions d = GraphStoreOptions.defaults();
        assertThat(d.lockTimeoutMillis()).isEqualTo(1000);
        assertThat(d.deadlockDetect()).isTrue();
        assertThat(d.syncWrites()).isFalse();
    }

    @Test
    void builderShouldRoundTripValues() {
        GraphStoreOptions o = GraphStoreOptions.builder()
            .lockTimeoutMillis(50)
            .deadlockDetect(false)
            .syncWrites(true)
            .build();
        assertThat(o.lockTimeoutMillis()).isEqualTo(50);
        assertThat(o.deadlockDetect()).isFalse();
        assertThat(o.syncWrites()).isTrue();
    }

    @Test
    void zeroLockTimeoutShouldFailFastOnContention() throws Exception {
        // With lockTimeout=0, a second transaction trying to lock a row
        // already held by another transaction must immediately error out
        // rather than wait. Use this when you'd rather retry at the
        // application layer than block.
        GraphStoreOptions options = GraphStoreOptions.builder()
            .lockTimeoutMillis(0)
            .build();
        try (GraphStore store = new GraphStore(tempDir.toString(), options)) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));

            // Hold a lock in t1, then try to grab the same row in t2.
            try (GraphTxn t1 = store.beginTransaction()) {
                t1.getVertex(PERSON_TYPE, 1L);     // takes write-lock on V|1|1
                try (GraphTxn t2 = store.beginTransaction()) {
                    assertThatThrownBy(() -> t2.getVertex(PERSON_TYPE, 1L))
                        .isInstanceOf(RocksDBException.class)
                        .satisfies(ex -> {
                            // Status should be one of the contention codes.
                            // On lock-timeout=0 RocksDB returns TimedOut.
                            Status.Code code = ((RocksDBException) ex).getStatus().getCode();
                            assertThat(code).isIn(Status.Code.TimedOut, Status.Code.Busy);
                        });
                    t2.rollback();
                }
                t1.rollback();
            }
        }
    }

    @Test
    void syncWritesShouldStillCommitData() throws Exception {
        // Smoke test: turning on sync mustn't break correctness, just
        // make it slower. We don't measure timing here; we just confirm
        // the committed value survives a re-open.
        GraphStoreOptions options = GraphStoreOptions.builder().syncWrites(true).build();
        try (GraphStore store = new GraphStore(tempDir.toString(), options)) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
        }
        try (GraphStore store = new GraphStore(tempDir.toString(), options)) {
            Vertex v = store.getVertex(PERSON_TYPE, 1L);
            assertThat(v).isNotNull();
            assertThat(v.getProperty("name")).isEqualTo("Alice");
        }
    }
}
