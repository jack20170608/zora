package top.ilovemyhome.zora.rocksdb.graph.store;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import top.ilovemyhome.zora.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParallelGraphImporterTest {

    private static final int PERSON_TYPE = 1;
    private static final int KNOWS_TYPE = 10;

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldImportAllVerticesAcrossWorkers() throws Exception {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            int count = 1_000;
            try (var importer = ParallelGraphImporter.vertexImporter(store, 4, 50)) {
                for (long id = 1; id <= count; id++) {
                    importer.submit(new Vertex(id, PERSON_TYPE)
                        .withProperty("name", "User" + id));
                }
            }   // close() blocks until everything is flushed
            for (long id = 1; id <= count; id++) {
                assertThat(store.getVertex(PERSON_TYPE, id)).isNotNull();
            }
        }
    }

    @Test
    void shouldImportAllEdgesAcrossWorkers() throws Exception {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            // Pre-create endpoint vertices.
            try (var v = ParallelGraphImporter.vertexImporter(store, 4, 100)) {
                for (long id = 1; id <= 100; id++) {
                    v.submit(new Vertex(id, PERSON_TYPE));
                }
            }
            try (var e = ParallelGraphImporter.edgeImporter(store, 4, 100)) {
                for (long src = 1; src <= 100; src++) {
                    for (long dst = 1; dst <= 100; dst++) {
                        if (src == dst) continue;
                        e.submit(new Edge(src, dst, KNOWS_TYPE)
                            .withProperty("weight", (int) ((src * 31 + dst) % 100)));
                    }
                }
            }
            // Spot-check one row's out-edges (each src has 99 distinct dsts).
            assertThat(store.getOutEdges(7L, KNOWS_TYPE)).hasSize(99);
        }
    }

    @Test
    void shouldRouteSameIdToSameWorkerDeterministically() throws Exception {
        // Submitting the same vertex id twice with conflicting properties
        // must serialise on the same worker (so the second one wins
        // deterministically) - never race two workers on the same row.
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            try (var importer = ParallelGraphImporter.vertexImporter(store, 8, 1)) {
                for (int i = 0; i < 100; i++) {
                    importer.submit(new Vertex(42L, PERSON_TYPE)
                        .withProperty("counter", i));
                }
            }
            // Whoever wrote last, the final value must be 99 - if hash
            // routing was broken two workers could have raced and the
            // value would be non-deterministic / lock-timeout could fire.
            assertThat(store.getVertex(PERSON_TYPE, 42L).getProperty("counter")).isEqualTo(99);
        }
    }

    @Test
    void shouldHandleEmptySubmissions() throws Exception {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            try (var importer = ParallelGraphImporter.vertexImporter(store, 4, 50)) {
                // Nothing submitted.
            }
            assertThat(store.getVertex(PERSON_TYPE, 1L)).isNull();
        }
    }

    @Test
    void shouldFailFastOnWriteError() throws Exception {
        // Submit an item type the codec can't handle to force a failure.
        // Submitting null forces NPE deep in the write path which is
        // caught and propagated as ImportException on close.
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            var importer = ParallelGraphImporter.vertexImporter(store, 2, 5);
            // Use a property type the encoder rejects: an unsupported class.
            importer.submit(new Vertex(1L, PERSON_TYPE).withProperty("bad", new Object()));
            // Pad with enough items to ensure the batch flushes.
            for (long id = 2; id <= 20; id++) {
                importer.submit(new Vertex(id, PERSON_TYPE));
            }
            assertThatThrownBy(importer::close)
                .isInstanceOf(ParallelGraphImporter.ImportException.class);
        }
    }

    @Test
    void shouldRejectSubmitAfterClose() throws Exception {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            var importer = ParallelGraphImporter.vertexImporter(store, 2, 5);
            importer.close();
            assertThatThrownBy(() -> importer.submit(new Vertex(1L, PERSON_TYPE)))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
