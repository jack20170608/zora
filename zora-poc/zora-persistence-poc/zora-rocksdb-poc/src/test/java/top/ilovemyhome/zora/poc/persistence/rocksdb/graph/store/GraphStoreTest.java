package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.store;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Vertex;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the GraphStore.
 * Covers vertex CRUD, bidirectional edge storage, neighbor traversal,
 * property indexing, and atomic batch operations.
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
        GraphStore store = new GraphStore(tempDir.toString());

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

        store.close();
    }

    @Test
    void shouldUpdateVertexProperties() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        Vertex v1 = new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice");
        store.addVertex(v1);

        Vertex v2 = new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice Smith").withProperty("age", 31);
        store.addVertex(v2);

        Vertex retrieved = store.getVertex(PERSON_TYPE, 1001L);
        assertThat(retrieved.getProperty("name")).isEqualTo("Alice Smith");
        assertThat(retrieved.getProperty("age")).isEqualTo(31);

        store.close();
    }

    @Test
    void shouldRemoveVertex() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        Vertex alice = new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice");
        store.addVertex(alice);

        store.removeVertex(PERSON_TYPE, 1001L);

        Vertex retrieved = store.getVertex(PERSON_TYPE, 1001L);
        assertThat(retrieved).isNull();

        store.close();
    }

    @Test
    void shouldGenerateSequentialVertexIds() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        long id1 = store.nextVertexId();
        long id2 = store.nextVertexId();
        long id3 = store.nextVertexId();

        assertThat(id2).isEqualTo(id1 + 1);
        assertThat(id3).isEqualTo(id2 + 1);

        store.close();
    }

    // ========================== Edge Tests ==========================

    @Test
    void shouldAddAndGetEdge() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        Edge edge = new Edge(1001L, 1002L, KNOWS_TYPE).withProperty("since", "2020");
        store.addEdge(edge);

        Edge retrieved = store.getEdge(1001L, KNOWS_TYPE, 1002L);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getSrcId()).isEqualTo(1001L);
        assertThat(retrieved.getDstId()).isEqualTo(1002L);
        assertThat(retrieved.getTypeId()).isEqualTo(KNOWS_TYPE);
        assertThat(retrieved.getProperties().get("since")).isEqualTo("2020");

        store.close();
    }

    @Test
    void shouldStoreBidirectionalEdges() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        // Create vertices
        store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));
        store.addVertex(new Vertex(1002L, PERSON_TYPE).withProperty("name", "Bob"));

        // Add edge
        store.addEdge(new Edge(1001L, 1002L, KNOWS_TYPE));

        // Verify outgoing edge from Alice
        List<Edge> outEdges = store.getOutEdges(1001L, KNOWS_TYPE);
        assertThat(outEdges).hasSize(1);
        assertThat(outEdges.get(0).getDstId()).isEqualTo(1002L);

        // Verify incoming edge to Bob
        List<Edge> inEdges = store.getInEdges(1002L, KNOWS_TYPE);
        assertThat(inEdges).hasSize(1);
        assertThat(inEdges.get(0).getSrcId()).isEqualTo(1001L);

        store.close();
    }

    @Test
    void shouldRemoveBidirectionalEdges() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        store.addEdge(new Edge(1001L, 1002L, KNOWS_TYPE));
        store.removeEdge(1001L, KNOWS_TYPE, 1002L);

        assertThat(store.getEdge(1001L, KNOWS_TYPE, 1002L)).isNull();
        assertThat(store.getOutEdges(1001L, KNOWS_TYPE)).isEmpty();
        assertThat(store.getInEdges(1002L, KNOWS_TYPE)).isEmpty();

        store.close();
    }

    @Test
    void shouldSupportMultipleEdgeTypes() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        store.addEdge(new Edge(1001L, 1002L, KNOWS_TYPE));
        store.addEdge(new Edge(1001L, 1002L, FOLLOWS_TYPE));

        assertThat(store.getOutEdges(1001L, KNOWS_TYPE)).hasSize(1);
        assertThat(store.getOutEdges(1001L, FOLLOWS_TYPE)).hasSize(1);

        store.close();
    }

    @Test
    void shouldSupportMultipleNeighbors() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

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

        store.close();
    }

    // ========================== Vertex Removal with Edges ==========================

    @Test
    void shouldRemoveVertexAndAllEdges() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));
        store.addVertex(new Vertex(1002L, PERSON_TYPE).withProperty("name", "Bob"));
        store.addVertex(new Vertex(1003L, PERSON_TYPE).withProperty("name", "Charlie"));

        store.addEdge(new Edge(1001L, 1002L, KNOWS_TYPE));
        store.addEdge(new Edge(1003L, 1001L, KNOWS_TYPE));

        store.removeVertex(PERSON_TYPE, 1001L);

        // Alice is gone
        assertThat(store.getVertex(PERSON_TYPE, 1001L)).isNull();

        // Edges connected to Alice are also removed
        assertThat(store.getOutEdges(1001L, KNOWS_TYPE)).isEmpty();
        assertThat(store.getInEdges(1001L, KNOWS_TYPE)).isEmpty();

        // Other vertices still exist
        assertThat(store.getVertex(PERSON_TYPE, 1002L)).isNotNull();
        assertThat(store.getVertex(PERSON_TYPE, 1003L)).isNotNull();

        store.close();
    }

    // ========================== Property Index Tests ==========================

    @Test
    void shouldFindVerticesByStringProperty() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));
        store.addVertex(new Vertex(1002L, PERSON_TYPE).withProperty("name", "Bob"));
        store.addVertex(new Vertex(1003L, PERSON_TYPE).withProperty("name", "Alice"));

        List<Vertex> result = store.findVerticesByProperty(PERSON_TYPE, "name", "Alice");
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Vertex::getId).containsExactlyInAnyOrder(1001L, 1003L);

        store.close();
    }

    @Test
    void shouldFindVerticesByIntegerProperty() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("age", 25));
        store.addVertex(new Vertex(1002L, PERSON_TYPE).withProperty("age", 30));
        store.addVertex(new Vertex(1003L, PERSON_TYPE).withProperty("age", 25));

        List<Vertex> result = store.findVerticesByProperty(PERSON_TYPE, "age", 25);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Vertex::getId).containsExactlyInAnyOrder(1001L, 1003L);

        store.close();
    }

    @Test
    void shouldReturnEmptyForMissingProperty() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        store.addVertex(new Vertex(1001L, PERSON_TYPE).withProperty("name", "Alice"));

        List<Vertex> result = store.findVerticesByProperty(PERSON_TYPE, "name", "Bob");
        assertThat(result).isEmpty();

        store.close();
    }

    // ========================== End-to-End Graph Scenario ==========================

    @Test
    void shouldHandleSocialGraphScenario() throws RocksDBException {
        GraphStore store = new GraphStore(tempDir.toString());

        // Create a small social graph
        store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice").withProperty("age", 30));
        store.addVertex(new Vertex(2L, PERSON_TYPE).withProperty("name", "Bob").withProperty("age", 25));
        store.addVertex(new Vertex(3L, PERSON_TYPE).withProperty("name", "Charlie").withProperty("age", 35));
        store.addVertex(new Vertex(4L, PERSON_TYPE).withProperty("name", "Diana").withProperty("age", 30));

        // Alice knows Bob and Charlie
        store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"));
        store.addEdge(new Edge(1L, 3L, KNOWS_TYPE).withProperty("since", "2021"));

        // Bob follows Diana
        store.addEdge(new Edge(2L, 4L, FOLLOWS_TYPE));

        // Charlie knows Diana
        store.addEdge(new Edge(3L, 4L, KNOWS_TYPE));

        // Verify Alice's friends
        List<Vertex> aliceFriends = store.getNeighbors(1L, KNOWS_TYPE);
        assertThat(aliceFriends).hasSize(2);
        assertThat(aliceFriends).extracting(v -> v.getProperty("name"))
            .containsExactlyInAnyOrder("Bob", "Charlie");

        // Verify Bob's followers (incoming follows)
        List<Edge> bobFollowers = store.getInEdges(2L, FOLLOWS_TYPE);
        assertThat(bobFollowers).isEmpty(); // No one follows Bob

        // Verify Diana's followers
        List<Edge> dianaFollowers = store.getInEdges(4L, FOLLOWS_TYPE);
        assertThat(dianaFollowers).hasSize(1);
        assertThat(dianaFollowers.get(0).getSrcId()).isEqualTo(2L);

        // Find people aged 30
        List<Vertex> age30 = store.findVerticesByProperty(PERSON_TYPE, "age", 30);
        assertThat(age30).hasSize(2);
        assertThat(age30).extracting(v -> v.getProperty("name"))
            .containsExactlyInAnyOrder("Alice", "Diana");

        // Verify edge properties
        Edge aliceToBob = store.getEdge(1L, KNOWS_TYPE, 2L);
        assertThat(aliceToBob.getProperties().get("since")).isEqualTo("2020");

        store.close();
    }
}
