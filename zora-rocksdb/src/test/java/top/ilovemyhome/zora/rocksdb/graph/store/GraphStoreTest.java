package top.ilovemyhome.zora.rocksdb.graph.store;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import top.ilovemyhome.zora.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the GraphStore.
 * Covers vertex CRUD, bidirectional edge storage, neighbour traversal,
 * property equality and range indexing, atomic batch operations,
 * and the schema dictionary lifecycle.
 */
class GraphStoreTest {

    private static final int PERSON_TYPE = 1;
    private static final int KNOWS_TYPE = 10;
    private static final int FOLLOWS_TYPE = 11;

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    // ========================== Vertex Tests ==========================

    @Test
    void shouldAddAndGetVertex() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            Vertex alice = new Vertex(1001L, PERSON_TYPE)
                .withProperty("name", "Alice")
                .withProperty("age", 30);

            store.addVertex(alice);

            Vertex retrieved = store.getVertex(PERSON_TYPE, 1001L);
            assertThat(retrieved).isNotNull();
            assertThat(retrieved.getId()).isEqualTo(1001L);
            assertThat(retrieved.getTypeId()).isEqualTo(PERSON_TYPE);
            assertThat(retrieved.getProperty("name")).isEqualTo("Alice");
            assertThat(retrieved.getProperty("age")).isEqualTo(30);
        }
    }

    @Test
    void shouldUpdateVertexProperties() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            Vertex v1 = new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice");
            store.addVertex(v1);

            Vertex v2 = new Vertex(1001L, PERSON_TYPE)
                .withProperty("name", "Alice Smith")
                .withProperty("age", 31);
            store.addVertex(v2);

            Vertex retrieved = store.getVertex(PERSON_TYPE, 1001L);
            assertThat(retrieved.getProperty("name")).isEqualTo("Alice Smith");
            assertThat(retrieved.getProperty("age")).isEqualTo(31);
        }
    }

    @Test
    void shouldCleanUpStaleIndexEntriesOnUpdate() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            // First version of the vertex; "Alice" gets indexed.
            store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice")).hasSize(1);

            // Rename to "Bob"; old index entry must be removed.
            store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Bob"));
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice")).isEmpty();
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Bob")).hasSize(1);
        }
    }

    @Test
    void shouldRemoveVertex() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            Vertex alice = new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice");
            store.addVertex(alice);

            store.removeVertex(PERSON_TYPE, 1001L);

            assertThat(store.getVertex(PERSON_TYPE, 1001L)).isNull();
            // Index entries are also gone.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice")).isEmpty();
        }
    }

    @Test
    void shouldGenerateSequentialVertexIds() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            long id1 = store.nextVertexId();
            long id2 = store.nextVertexId();
            long id3 = store.nextVertexId();

            assertThat(id2).isEqualTo(id1 + 1);
            assertThat(id3).isEqualTo(id2 + 1);
        }
    }

    // ========================== Edge Tests ==========================

    @Test
    void shouldAddAndGetEdge() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            Edge edge = new Edge(1001L, 1002L, KNOWS_TYPE).withProperty("since", "2020");
            store.addEdge(edge);

            Edge retrieved = store.getEdge(1001L, KNOWS_TYPE, 1002L);
            assertThat(retrieved).isNotNull();
            assertThat(retrieved.getSrcId()).isEqualTo(1001L);
            assertThat(retrieved.getDstId()).isEqualTo(1002L);
            assertThat(retrieved.getTypeId()).isEqualTo(KNOWS_TYPE);
            assertThat(retrieved.getProperties().get("since")).isEqualTo("2020");
        }
    }

    @Test
    void shouldStoreBidirectionalEdges() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));
            store.addVertex(new Vertex(1002L, PERSON_TYPE).withProperty("name", "Bob"));

            store.addEdge(new Edge(1001L, 1002L, KNOWS_TYPE));

            List<Edge> outEdges = store.getOutEdges(1001L, KNOWS_TYPE);
            assertThat(outEdges).hasSize(1);
            assertThat(outEdges.get(0).getDstId()).isEqualTo(1002L);

            List<Edge> inEdges = store.getInEdges(1002L, KNOWS_TYPE);
            assertThat(inEdges).hasSize(1);
            assertThat(inEdges.get(0).getSrcId()).isEqualTo(1001L);
        }
    }

    @Test
    void shouldRemoveBidirectionalEdges() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addEdge(new Edge(1001L, 1002L, KNOWS_TYPE));
            store.removeEdge(1001L, KNOWS_TYPE, 1002L);

            assertThat(store.getEdge(1001L, KNOWS_TYPE, 1002L)).isNull();
            assertThat(store.getOutEdges(1001L, KNOWS_TYPE)).isEmpty();
            assertThat(store.getInEdges(1002L, KNOWS_TYPE)).isEmpty();
        }
    }

    @Test
    void shouldSupportMultipleEdgeTypes() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addEdge(new Edge(1001L, 1002L, KNOWS_TYPE));
            store.addEdge(new Edge(1001L, 1002L, FOLLOWS_TYPE));

            assertThat(store.getOutEdges(1001L, KNOWS_TYPE)).hasSize(1);
            assertThat(store.getOutEdges(1001L, FOLLOWS_TYPE)).hasSize(1);
        }
    }

    @Test
    void shouldSupportMultipleNeighbors() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));
            store.addVertex(new Vertex(1002L, PERSON_TYPE).withProperty("name", "Bob"));
            store.addVertex(new Vertex(1003L, PERSON_TYPE).withProperty("name", "Charlie"));

            store.addEdge(new Edge(1001L, 1002L, KNOWS_TYPE));
            store.addEdge(new Edge(1001L, 1003L, KNOWS_TYPE));

            List<Edge> outEdges = store.getOutEdges(1001L, KNOWS_TYPE);
            assertThat(outEdges).hasSize(2);

            List<Vertex> neighbors = store.getNeighbors(1001L, KNOWS_TYPE);
            assertThat(neighbors).hasSize(2);
            assertThat(neighbors).extracting(v -> v.getProperty("name"))
                .containsExactlyInAnyOrder("Bob", "Charlie");
        }
    }

    // ========================== Vertex Removal with Edges ==========================

    @Test
    void shouldRemoveVertexAndAllEdges() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));
            store.addVertex(new Vertex(1002L, PERSON_TYPE).withProperty("name", "Bob"));
            store.addVertex(new Vertex(1003L, PERSON_TYPE).withProperty("name", "Charlie"));

            store.addEdge(new Edge(1001L, 1002L, KNOWS_TYPE));
            store.addEdge(new Edge(1003L, 1001L, KNOWS_TYPE));

            store.removeVertex(PERSON_TYPE, 1001L);

            assertThat(store.getVertex(PERSON_TYPE, 1001L)).isNull();
            assertThat(store.getOutEdges(1001L, KNOWS_TYPE)).isEmpty();
            assertThat(store.getInEdges(1001L, KNOWS_TYPE)).isEmpty();
            assertThat(store.getVertex(PERSON_TYPE, 1002L)).isNotNull();
            assertThat(store.getVertex(PERSON_TYPE, 1003L)).isNotNull();
        }
    }

    // ========================== Property Index Tests ==========================

    @Test
    void shouldFindVerticesByStringProperty() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));
            store.addVertex(new Vertex(1002L, PERSON_TYPE).withProperty("name", "Bob"));
            store.addVertex(new Vertex(1003L, PERSON_TYPE).withProperty("name", "Alice"));

            List<Vertex> result = store.findVerticesByProperty(PERSON_TYPE, "name", "Alice");
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Vertex::getId).containsExactlyInAnyOrder(1001L, 1003L);
        }
    }

    @Test
    void shouldNotMatchByStringPrefix() throws RocksDBException {
        // Regression: with the self-terminating string encoding, "Alic"
        // must not match a vertex whose name is "Alice".
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));
            store.addVertex(new Vertex(1002L, PERSON_TYPE).withProperty("name", "Alicia"));

            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alic")).isEmpty();
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice"))
                .extracting(Vertex::getId).containsExactly(1001L);
        }
    }

    @Test
    void shouldFindVerticesByIntegerProperty() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("age", 25));
            store.addVertex(new Vertex(1002L, PERSON_TYPE).withProperty("age", 30));
            store.addVertex(new Vertex(1003L, PERSON_TYPE).withProperty("age", 25));

            List<Vertex> result = store.findVerticesByProperty(PERSON_TYPE, "age", 25);
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Vertex::getId).containsExactlyInAnyOrder(1001L, 1003L);
        }
    }

    @Test
    void shouldReturnEmptyForMissingProperty() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));

            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Bob")).isEmpty();
            // Unknown property name short-circuits without scanning.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "nickname", "x")).isEmpty();
        }
    }

    @Test
    void shouldFindVerticesByIntegerRange() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("age", 20));
            store.addVertex(new Vertex(2L, PERSON_TYPE).withProperty("age", 25));
            store.addVertex(new Vertex(3L, PERSON_TYPE).withProperty("age", 30));
            store.addVertex(new Vertex(4L, PERSON_TYPE).withProperty("age", 35));
            store.addVertex(new Vertex(5L, PERSON_TYPE).withProperty("age", 40));

            // Inclusive on both ends.
            List<Vertex> mid = store.findVerticesByPropertyRange(PERSON_TYPE, "age", 25, 35);
            assertThat(mid).extracting(Vertex::getId).containsExactlyInAnyOrder(2L, 3L, 4L);

            // Single-value range degenerates to equality.
            List<Vertex> single = store.findVerticesByPropertyRange(PERSON_TYPE, "age", 30, 30);
            assertThat(single).extracting(Vertex::getId).containsExactly(3L);
        }
    }

    @Test
    void shouldHandleNegativeNumbersInRange() throws RocksDBException {
        // Sign-bit-flip encoding has to keep negative numbers ordered too.
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("balance", -100));
            store.addVertex(new Vertex(2L, PERSON_TYPE).withProperty("balance", -10));
            store.addVertex(new Vertex(3L, PERSON_TYPE).withProperty("balance", 0));
            store.addVertex(new Vertex(4L, PERSON_TYPE).withProperty("balance", 10));

            List<Vertex> negatives =
                store.findVerticesByPropertyRange(PERSON_TYPE, "balance", -200, -1);
            assertThat(negatives).extracting(Vertex::getId).containsExactlyInAnyOrder(1L, 2L);
        }
    }

    @Test
    void shouldFindVerticesByDoubleRange() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("score", 1.5));
            store.addVertex(new Vertex(2L, PERSON_TYPE).withProperty("score", 2.7));
            store.addVertex(new Vertex(3L, PERSON_TYPE).withProperty("score", 3.14));
            store.addVertex(new Vertex(4L, PERSON_TYPE).withProperty("score", -0.5));

            List<Vertex> mid =
                store.findVerticesByPropertyRange(PERSON_TYPE, "score", 1.0, 3.0);
            assertThat(mid).extracting(Vertex::getId).containsExactlyInAnyOrder(1L, 2L);
        }
    }

    @Test
    void shouldFindVerticesByStringRange() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
            store.addVertex(new Vertex(2L, PERSON_TYPE).withProperty("name", "Bob"));
            store.addVertex(new Vertex(3L, PERSON_TYPE).withProperty("name", "Charlie"));
            store.addVertex(new Vertex(4L, PERSON_TYPE).withProperty("name", "David"));

            List<Vertex> bToC =
                store.findVerticesByPropertyRange(PERSON_TYPE, "name", "B", "Cz");
            assertThat(bToC).extracting(Vertex::getId).containsExactlyInAnyOrder(2L, 3L);
        }
    }

    // ========================== Schema Dictionary ==========================

    @Test
    void shouldPersistPropertyDictionaryAcrossReopens() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
        }
        // Reopen and verify the index still resolves "name" -> the same propId.
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice"))
                .extracting(Vertex::getId).containsExactly(1L);
        }
    }

    // ========================== End-to-End Graph Scenario ==========================

    @Test
    void shouldHandleSocialGraphScenario() throws RocksDBException {
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice").withProperty("age", 30));
            store.addVertex(new Vertex(2L, PERSON_TYPE).withProperty("name", "Bob").withProperty("age", 25));
            store.addVertex(new Vertex(3L, PERSON_TYPE).withProperty("name", "Charlie").withProperty("age", 35));
            store.addVertex(new Vertex(4L, PERSON_TYPE).withProperty("name", "Diana").withProperty("age", 30));

            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"));
            store.addEdge(new Edge(1L, 3L, KNOWS_TYPE).withProperty("since", "2021"));
            store.addEdge(new Edge(2L, 4L, FOLLOWS_TYPE));
            store.addEdge(new Edge(3L, 4L, KNOWS_TYPE));

            List<Vertex> aliceFriends = store.getNeighbors(1L, KNOWS_TYPE);
            assertThat(aliceFriends).extracting(v -> v.getProperty("name"))
                .containsExactlyInAnyOrder("Bob", "Charlie");

            assertThat(store.getInEdges(2L, FOLLOWS_TYPE)).isEmpty();

            List<Edge> dianaFollowers = store.getInEdges(4L, FOLLOWS_TYPE);
            assertThat(dianaFollowers).hasSize(1);
            assertThat(dianaFollowers.get(0).getSrcId()).isEqualTo(2L);

            // Equality: people aged exactly 30.
            List<Vertex> age30 = store.findVerticesByProperty(PERSON_TYPE, "age", 30);
            assertThat(age30).extracting(v -> v.getProperty("name"))
                .containsExactlyInAnyOrder("Alice", "Diana");

            // Range: 26..34
            List<Vertex> midAge = store.findVerticesByPropertyRange(PERSON_TYPE, "age", 26, 34);
            assertThat(midAge).extracting(v -> v.getProperty("name"))
                .containsExactlyInAnyOrder("Alice", "Diana");

            Edge aliceToBob = store.getEdge(1L, KNOWS_TYPE, 2L);
            assertThat(aliceToBob.getProperties().get("since")).isEqualTo("2020");
        }
    }
}
