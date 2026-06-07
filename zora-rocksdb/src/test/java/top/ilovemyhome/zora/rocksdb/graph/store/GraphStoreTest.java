package top.ilovemyhome.zora.rocksdb.graph.store;

import static top.ilovemyhome.zora.rocksdb.graph.store.TestSupport.openTestStore;
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(1001L, 1002L, KNOWS_TYPE));
            store.removeEdge(1001L, KNOWS_TYPE, 1002L);

            assertThat(store.getEdge(1001L, KNOWS_TYPE, 1002L)).isNull();
            assertThat(store.getOutEdges(1001L, KNOWS_TYPE)).isEmpty();
            assertThat(store.getInEdges(1002L, KNOWS_TYPE)).isEmpty();
        }
    }

    @Test
    void shouldSupportMultipleEdgeTypes() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(1001L, 1002L, KNOWS_TYPE));
            store.addEdge(new Edge(1001L, 1002L, FOLLOWS_TYPE));

            assertThat(store.getOutEdges(1001L, KNOWS_TYPE)).hasSize(1);
            assertThat(store.getOutEdges(1001L, FOLLOWS_TYPE)).hasSize(1);
        }
    }

    @Test
    void shouldSupportMultipleNeighbors() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));
            store.addVertex(new Vertex(1002L, PERSON_TYPE).withProperty("name", "Alicia"));

            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alic")).isEmpty();
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice"))
                .extracting(Vertex::getId).containsExactly(1001L);
        }
    }

    @Test
    void shouldFindVerticesByIntegerProperty() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));

            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Bob")).isEmpty();
            // Unknown property name short-circuits without scanning.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "nickname", "x")).isEmpty();
        }
    }

    @Test
    void shouldFindVerticesByIntegerRange() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
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
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
        }
        // Reopen and verify the index still resolves "name" -> the same propId.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice"))
                .extracting(Vertex::getId).containsExactly(1L);
        }
    }

    // ========================== End-to-End Graph Scenario ==========================

    @Test
    void shouldHandleSocialGraphScenario() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
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

    // ========================== Edge Property Index ==========================

    @Test
    void shouldFindEdgesByPropertyGlobally() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"));
            store.addEdge(new Edge(1L, 3L, KNOWS_TYPE).withProperty("since", "2021"));
            store.addEdge(new Edge(2L, 3L, KNOWS_TYPE).withProperty("since", "2020"));
            // Different edge type with same value must not bleed in.
            store.addEdge(new Edge(1L, 2L, FOLLOWS_TYPE).withProperty("since", "2020"));

            List<Edge> matches = store.findEdgesByProperty(KNOWS_TYPE, "since", "2020");
            assertThat(matches).hasSize(2);
            assertThat(matches)
                .extracting(e -> e.getSrcId() + "->" + e.getDstId())
                .containsExactlyInAnyOrder("1->2", "2->3");
        }
    }

    @Test
    void shouldFindEdgesByIntegerPropertyRange() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("weight", 1));
            store.addEdge(new Edge(1L, 3L, KNOWS_TYPE).withProperty("weight", 5));
            store.addEdge(new Edge(2L, 3L, KNOWS_TYPE).withProperty("weight", 10));
            store.addEdge(new Edge(2L, 4L, KNOWS_TYPE).withProperty("weight", 20));

            List<Edge> mid = store.findEdgesByPropertyRange(KNOWS_TYPE, "weight", 5, 15);
            assertThat(mid)
                .extracting(e -> e.getSrcId() + "->" + e.getDstId())
                .containsExactlyInAnyOrder("1->3", "2->3");
        }
    }

    @Test
    void shouldFindOutAndInEdgesByProperty() throws RocksDBException {
        // Endpoint-keyed flavour: filter Alice's OUT edges by since=2020 without
        // intersecting two separate scans in user code.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"));
            store.addEdge(new Edge(1L, 3L, KNOWS_TYPE).withProperty("since", "2021"));
            store.addEdge(new Edge(1L, 4L, KNOWS_TYPE).withProperty("since", "2020"));
            store.addEdge(new Edge(5L, 1L, KNOWS_TYPE).withProperty("since", "2020"));

            List<Edge> aliceOut2020 = store.findOutEdgesByProperty(1L, KNOWS_TYPE, "since", "2020");
            assertThat(aliceOut2020).extracting(Edge::getDstId).containsExactlyInAnyOrder(2L, 4L);

            // Symmetric: Alice as destination, only the incoming 5->1 should match.
            List<Edge> aliceIn2020 = store.findInEdgesByProperty(1L, KNOWS_TYPE, "since", "2020");
            assertThat(aliceIn2020).extracting(Edge::getSrcId).containsExactly(5L);
        }
    }

    @Test
    void shouldCleanUpStaleEdgeIndexOnPropertyUpdate() throws RocksDBException {
        // addEdge re-runs as an upsert; the previous "since=2020" index entries
        // (all three flavours) must disappear when we rewrite the same edge
        // with "since=2099".
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"));
            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "since", "2020")).hasSize(1);

            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2099"));

            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "since", "2020")).isEmpty();
            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "since", "2099")).hasSize(1);
            assertThat(store.findOutEdgesByProperty(1L, KNOWS_TYPE, "since", "2020")).isEmpty();
            assertThat(store.findInEdgesByProperty(2L, KNOWS_TYPE, "since", "2020")).isEmpty();
        }
    }

    @Test
    void shouldCleanUpEdgeIndexOnRemoveEdge() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"));
            store.removeEdge(1L, KNOWS_TYPE, 2L);

            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "since", "2020")).isEmpty();
            assertThat(store.findOutEdgesByProperty(1L, KNOWS_TYPE, "since", "2020")).isEmpty();
            assertThat(store.findInEdgesByProperty(2L, KNOWS_TYPE, "since", "2020")).isEmpty();
        }
    }

    @Test
    void shouldCleanUpEdgeIndexOnRemoveVertex() throws RocksDBException {
        // Deleting a vertex must cascade-delete every incident edge AND each
        // edge's 3-way index entries; otherwise findEdgesByProperty returns
        // ghost edges that the user can't even resolve to a real Edge.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
            store.addVertex(new Vertex(2L, PERSON_TYPE).withProperty("name", "Bob"));
            store.addVertex(new Vertex(3L, PERSON_TYPE).withProperty("name", "Charlie"));
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"));
            store.addEdge(new Edge(3L, 1L, KNOWS_TYPE).withProperty("since", "2020"));

            store.removeVertex(PERSON_TYPE, 1L);

            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "since", "2020")).isEmpty();
            assertThat(store.findOutEdgesByProperty(3L, KNOWS_TYPE, "since", "2020")).isEmpty();
        }
    }

    @Test
    void shouldShareSchemaDictionaryAcrossVertexAndEdge() throws RocksDBException {
        // Single propId namespace - resolving "name" via addVertex must be the
        // same id later used by edge indexing for the property "name".
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("name", "best-friend"));

            // Both indexes must be reachable through the same name.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice"))
                .extracting(Vertex::getId).containsExactly(1L);
            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "name", "best-friend"))
                .extracting(e -> e.getSrcId() + "->" + e.getDstId())
                .containsExactly("1->2");
        }
    }

    @Test
    void shouldPersistEdgeIndexAcrossReopens() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"));
        }
        try (GraphStore store = openTestStore(tempDir.toString())) {
            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "since", "2020"))
                .extracting(e -> e.getSrcId() + "->" + e.getDstId())
                .containsExactly("1->2");
        }
    }

    @Test
    void shouldFindOutEdgesByPropertyRange() throws RocksDBException {
        // Endpoint + range filter without any user-side intersection.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("weight", 1));
            store.addEdge(new Edge(1L, 3L, KNOWS_TYPE).withProperty("weight", 5));
            store.addEdge(new Edge(1L, 4L, KNOWS_TYPE).withProperty("weight", 10));
            store.addEdge(new Edge(1L, 5L, KNOWS_TYPE).withProperty("weight", 20));
            store.addEdge(new Edge(7L, 8L, KNOWS_TYPE).withProperty("weight", 7));

            List<Edge> mid = store.findOutEdgesByPropertyRange(1L, KNOWS_TYPE, "weight", 5, 15);
            assertThat(mid).extracting(Edge::getDstId).containsExactlyInAnyOrder(3L, 4L);
            // Crucially, the (7,8) edge with weight=7 must NOT show up: it's an
            // out-edge of vertex 7, not of vertex 1.

            // Lower-only edge case: high == max value in range
            List<Edge> hi = store.findOutEdgesByPropertyRange(1L, KNOWS_TYPE, "weight", 15, 100);
            assertThat(hi).extracting(Edge::getDstId).containsExactly(5L);
        }
    }

    @Test
    void shouldFindInEdgesByPropertyRange() throws RocksDBException {
        // Same shape, dst-keyed flavour.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(2L, 1L, KNOWS_TYPE).withProperty("weight", 1));
            store.addEdge(new Edge(3L, 1L, KNOWS_TYPE).withProperty("weight", 5));
            store.addEdge(new Edge(4L, 1L, KNOWS_TYPE).withProperty("weight", 10));
            store.addEdge(new Edge(5L, 6L, KNOWS_TYPE).withProperty("weight", 7));

            List<Edge> mid = store.findInEdgesByPropertyRange(1L, KNOWS_TYPE, "weight", 5, 15);
            assertThat(mid).extracting(Edge::getSrcId).containsExactlyInAnyOrder(3L, 4L);
        }
    }

    // ========================== Reverse Index (vertexId -> typeId) ==========================

    @Test
    void shouldResolveVertexByIdAcrossMultipleTypes() throws RocksDBException {
        // The reverse index makes getVertex(long) a point lookup regardless
        // of typeId. This used to silently rely on a full cf_vertex scan.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
            store.addVertex(new Vertex(2L, PERSON_TYPE + 5).withProperty("name", "Acme Corp"));
            store.addVertex(new Vertex(3L, PERSON_TYPE).withProperty("name", "Bob"));

            assertThat(store.getVertex(1L).getProperty("name")).isEqualTo("Alice");
            assertThat(store.getVertex(2L).getProperty("name")).isEqualTo("Acme Corp");
            assertThat(store.getVertex(3L).getProperty("name")).isEqualTo("Bob");
            assertThat(store.getVertex(999L)).isNull();
        }
    }

    @Test
    void shouldRemoveReverseIndexEntryOnVertexDelete() throws RocksDBException {
        // After removeVertex, the reverse entry must also be gone so
        // getVertex(long) returns null without ever fall-backing to a scan
        // (which would have also returned null but slower).
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
            assertThat(store.getVertex(1L)).isNotNull();
            store.removeVertex(PERSON_TYPE, 1L);
            assertThat(store.getVertex(1L)).isNull();
        }
    }

    // ========================== Concurrency ==========================

    @Test
    void shouldKeepIndexConsistentUnderConcurrentChurn() throws Exception {
        // TransactionDB does NOT magically make addVertex a field-level merge -
        // it's still a whole-object overwrite, so last-writer-wins is normal
        // and expected. What the pessimistic lock DOES guarantee is that the
        // "read previous + delete stale index + put new vertex + put new
        // index" sequence inside addVertex commits atomically against
        // concurrent writers - so the secondary index never points at a
        // vertex whose stored value disagrees.
        //
        // This test stresses the same vertex from many threads, each
        // randomly choosing one of N city values. After the storm settles,
        // findVerticesByProperty must return the vertex exactly when the
        // vertex's persisted "city" property matches the query - never
        // returning a ghost match, never missing a real one.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("city", "BJ"));

            String[] cities = {"BJ", "SH", "SZ", "HZ", "GZ"};
            int threadCount = 8;
            int iterPerThread = 100;
            java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            try {
                java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
                for (int t = 0; t < threadCount; t++) {
                    final int seed = t;
                    futures.add(pool.submit(() -> {
                        java.util.Random rng = new java.util.Random(seed);
                        for (int i = 0; i < iterPerThread; i++) {
                            try {
                                String city = cities[rng.nextInt(cities.length)];
                                store.addVertex(new Vertex(1L, PERSON_TYPE)
                                    .withProperty("city", city));
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

            // Whichever value won the race, the vertex + index must agree:
            Vertex finalVertex = store.getVertex(PERSON_TYPE, 1L);
            assertThat(finalVertex).isNotNull();
            String finalCity = (String) finalVertex.getProperty("city");
            assertThat(finalCity).isIn((Object[]) cities);

            // The winning city must locate vertex 1 via the index.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "city", finalCity))
                .extracting(Vertex::getId).contains(1L);

            // Every losing city must NOT return vertex 1 - no stale index.
            for (String losing : cities) {
                if (losing.equals(finalCity)) continue;
                assertThat(store.findVerticesByProperty(PERSON_TYPE, "city", losing))
                    .extracting(Vertex::getId).doesNotContain(1L);
            }
        }
    }

    @Test
    void shouldGenerateUniqueIdsUnderContention() throws Exception {
        // nextVertexId() runs as its own mini-txn; verify two threads never
        // get the same number back even when racing tightly.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            int threadCount = 8;
            int iterPerThread = 200;
            java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            java.util.Set<Long> ids =
                java.util.concurrent.ConcurrentHashMap.newKeySet();
            try {
                java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
                for (int t = 0; t < threadCount; t++) {
                    futures.add(pool.submit(() -> {
                        for (int i = 0; i < iterPerThread; i++) {
                            try {
                                ids.add(store.nextVertexId());
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
            assertThat(ids).hasSize(threadCount * iterPerThread);
        }
    }

    // ========================== No-op short-circuit ==========================

    @Test
    void shouldKeepIndexIntactWhenAddVertexIsNoOp() throws RocksDBException {
        // Repeated addVertex with identical properties must not corrupt the
        // index. With the short-circuit it's literally a no-op; this test
        // pins that behaviour so future refactors can't quietly break it.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE)
                .withProperty("name", "Alice").withProperty("age", 30));

            for (int i = 0; i < 10; i++) {
                store.addVertex(new Vertex(1L, PERSON_TYPE)
                    .withProperty("name", "Alice").withProperty("age", 30));
            }

            assertThat(store.getVertex(PERSON_TYPE, 1L).getProperty("name")).isEqualTo("Alice");
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice"))
                .extracting(Vertex::getId).containsExactly(1L);
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "age", 30))
                .extracting(Vertex::getId).containsExactly(1L);
        }
    }

    @Test
    void shouldKeepIndexIntactWhenAddEdgeIsNoOp() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"));

            for (int i = 0; i < 10; i++) {
                store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"));
            }

            assertThat(store.getEdge(1L, KNOWS_TYPE, 2L).getProperties().get("since")).isEqualTo("2020");
            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "since", "2020")).hasSize(1);
            assertThat(store.findOutEdgesByProperty(1L, KNOWS_TYPE, "since", "2020"))
                .extracting(Edge::getDstId).containsExactly(2L);
        }
    }

    // ========================== Bulk API ==========================

    @Test
    void shouldAddVerticesInBulkAtomically() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertices(List.of(
                new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"),
                new Vertex(2L, PERSON_TYPE).withProperty("name", "Bob"),
                new Vertex(3L, PERSON_TYPE).withProperty("name", "Charlie")));

            for (long id = 1; id <= 3; id++) {
                assertThat(store.getVertex(PERSON_TYPE, id)).isNotNull();
            }
            // Indexes also populated for every batch element.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Bob"))
                .extracting(Vertex::getId).containsExactly(2L);
        }
    }

    @Test
    void shouldAddEdgesInBulkAtomically() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertices(List.of(
                new Vertex(1L, PERSON_TYPE),
                new Vertex(2L, PERSON_TYPE),
                new Vertex(3L, PERSON_TYPE)));

            store.addEdges(List.of(
                new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"),
                new Edge(2L, 3L, KNOWS_TYPE).withProperty("since", "2021"),
                new Edge(1L, 3L, KNOWS_TYPE).withProperty("since", "2020")));

            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "since", "2020")).hasSize(2);
            assertThat(store.getOutEdges(1L, KNOWS_TYPE)).hasSize(2);
        }
    }

    @Test
    void shouldHandleEmptyBulkInputs() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertices(List.of());
            store.addEdges(List.of());
            // Nothing should have been touched.
            assertThat(store.getVertex(PERSON_TYPE, 1L)).isNull();
        }
    }

    // ========================== Partial Update ==========================

    @Test
    void shouldPartiallyUpdateVertexAndKeepUnmentionedProperties() throws RocksDBException {
        // Headline behaviour difference vs addVertex: properties NOT in the
        // changes map must be preserved, not wiped out.
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE)
                .withProperty("name", "Alice")
                .withProperty("age", 30)
                .withProperty("city", "BJ"));

            boolean changed = store.updateVertexProperty(PERSON_TYPE, 1L, "age", 31);
            assertThat(changed).isTrue();

            Vertex v = store.getVertex(PERSON_TYPE, 1L);
            assertThat(v.getProperty("name")).isEqualTo("Alice");   // preserved
            assertThat(v.getProperty("age")).isEqualTo(31);         // updated
            assertThat(v.getProperty("city")).isEqualTo("BJ");      // preserved
        }
    }

    @Test
    void shouldKeepIndexConsistentAfterPartialVertexUpdate() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE)
                .withProperty("name", "Alice").withProperty("age", 30));

            store.updateVertexProperty(PERSON_TYPE, 1L, "age", 31);

            // Old age=30 entry should be gone, new age=31 should be present.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "age", 30)).isEmpty();
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "age", 31))
                .extracting(Vertex::getId).containsExactly(1L);
            // Untouched property index unchanged.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice"))
                .extracting(Vertex::getId).containsExactly(1L);
        }
    }

    @Test
    void shouldRemovePropertyWhenUpdateValueIsNull() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE)
                .withProperty("name", "Alice").withProperty("age", 30));

            boolean changed = store.updateVertexProperty(PERSON_TYPE, 1L, "age", null);
            assertThat(changed).isTrue();

            Vertex v = store.getVertex(PERSON_TYPE, 1L);
            assertThat(v.getProperties()).containsOnlyKeys("name");
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "age", 30)).isEmpty();
        }
    }

    @Test
    void shouldReturnFalseWhenUpdateMatchesCurrentState() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("age", 30));
            // Same value -> no-op short-circuit -> false.
            assertThat(store.updateVertexProperty(PERSON_TYPE, 1L, "age", 30)).isFalse();
            // Deleting a missing key -> no-op -> false.
            assertThat(store.updateVertexProperty(PERSON_TYPE, 1L, "missing", null)).isFalse();
        }
    }

    @Test
    void shouldReturnFalseForUpdateOnMissingVertex() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            // Vertex 999 does not exist.
            assertThat(store.updateVertexProperty(PERSON_TYPE, 999L, "age", 1)).isFalse();
        }
    }

    @Test
    void shouldUpdateMultipleVertexPropertiesAtOnce() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE)
                .withProperty("name", "Alice")
                .withProperty("age", 30)
                .withProperty("city", "BJ"));

            // Map containing null -> use HashMap (Map.of disallows nulls).
            java.util.HashMap<String, Object> changes = new java.util.HashMap<>();
            changes.put("age", 31);                // update
            changes.put("city", null);             // delete
            changes.put("country", "CN");          // insert
            // "name" intentionally absent -> preserved.

            assertThat(store.updateVertexProperties(PERSON_TYPE, 1L, changes)).isTrue();

            Vertex v = store.getVertex(PERSON_TYPE, 1L);
            assertThat(v.getProperty("name")).isEqualTo("Alice");
            assertThat(v.getProperty("age")).isEqualTo(31);
            assertThat(v.getProperty("country")).isEqualTo("CN");
            assertThat(v.getProperties()).doesNotContainKey("city");
        }
    }

    @Test
    void shouldPartiallyUpdateEdge() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE)
                .withProperty("since", "2020").withProperty("weight", 5));

            boolean changed = store.updateEdgeProperty(1L, KNOWS_TYPE, 2L, "weight", 10);
            assertThat(changed).isTrue();

            Edge e = store.getEdge(1L, KNOWS_TYPE, 2L);
            assertThat(e.getProperties().get("since")).isEqualTo("2020");   // preserved
            assertThat(e.getProperties().get("weight")).isEqualTo(10);      // updated

            // All three edge index flavours must reflect the new value.
            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "weight", 5)).isEmpty();
            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "weight", 10)).hasSize(1);
            assertThat(store.findOutEdgesByProperty(1L, KNOWS_TYPE, "weight", 10)).hasSize(1);
            assertThat(store.findInEdgesByProperty(2L, KNOWS_TYPE, "weight", 10)).hasSize(1);
        }
    }

    @Test
    void shouldReturnFalseForUpdateOnMissingEdge() throws RocksDBException {
        try (GraphStore store = openTestStore(tempDir.toString())) {
            assertThat(store.updateEdgeProperty(1L, KNOWS_TYPE, 2L, "x", 1)).isFalse();
        }
    }
}
