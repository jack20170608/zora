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

/**
 * Tests for {@link IndexPolicy} integration: only declared properties
 * are written to / readable from the secondary indexes.
 */
class IndexPolicyTest {

    private static final int PERSON_TYPE  = 1;
    private static final int PRODUCT_TYPE = 2;
    private static final int KNOWS_TYPE   = 10;

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    private GraphStore open(IndexPolicy policy) throws RocksDBException {
        return new GraphStore(tempDir.toString(),
            GraphStoreOptions.builder().indexPolicy(policy).build());
    }

    // ========================== Defaults ==========================

    @Test
    void defaultPolicyIsNone() throws RocksDBException {
        // Without explicit options, addVertex writes nothing into cf_index,
        // so find* returns empty even if the property is there.
        try (GraphStore store = new GraphStore(tempDir.toString())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
            assertThat(store.getVertex(PERSON_TYPE, 1L).getProperty("name")).isEqualTo("Alice");
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice")).isEmpty();
        }
    }

    // ========================== Allow-list scoping ==========================

    @Test
    void shouldOnlyIndexDeclaredVertexProperties() throws RocksDBException {
        IndexPolicy policy = IndexPolicy.builder()
            .indexVertexProperty(PERSON_TYPE, "name")
            .build();
        try (GraphStore store = open(policy)) {
            store.addVertex(new Vertex(1L, PERSON_TYPE)
                .withProperty("name", "Alice")
                .withProperty("age", 30));

            // name is indexed -> queryable.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice"))
                .extracting(Vertex::getId).containsExactly(1L);
            // age is not -> find returns empty even though the value is there.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "age", 30)).isEmpty();
            assertThat(store.getVertex(PERSON_TYPE, 1L).getProperty("age")).isEqualTo(30);
        }
    }

    @Test
    void shouldScopeVertexIndexByType() throws RocksDBException {
        // PERSON indexes "name", PRODUCT does not. Two vertices with the
        // same prop name on different types must not mix.
        IndexPolicy policy = IndexPolicy.builder()
            .indexVertexProperty(PERSON_TYPE, "name")
            .build();
        try (GraphStore store = open(policy)) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
            store.addVertex(new Vertex(2L, PRODUCT_TYPE).withProperty("name", "Widget"));

            assertThat(store.findVerticesByProperty(PERSON_TYPE,  "name", "Alice"))
                .extracting(Vertex::getId).containsExactly(1L);
            // PRODUCT.name not indexed -> empty.
            assertThat(store.findVerticesByProperty(PRODUCT_TYPE, "name", "Widget")).isEmpty();
        }
    }

    @Test
    void shouldScopeEdgeIndexByType() throws RocksDBException {
        IndexPolicy policy = IndexPolicy.builder()
            .indexEdgeProperty(KNOWS_TYPE, "since")
            .build();
        try (GraphStore store = open(policy)) {
            store.addEdge(new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020"));
            // weight not declared -> not indexed.
            store.addEdge(new Edge(1L, 3L, KNOWS_TYPE)
                .withProperty("since", "2021").withProperty("weight", 5));

            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "since", "2020")).hasSize(1);
            assertThat(store.findEdgesByProperty(KNOWS_TYPE, "weight", 5)).isEmpty();
            // Main store still has the value.
            assertThat(store.getEdge(1L, KNOWS_TYPE, 3L).getProperties().get("weight"))
                .isEqualTo(5);
        }
    }

    // ========================== Read/write parity ==========================

    @Test
    void shouldNotWriteIndexForUndeclaredProperty() throws RocksDBException {
        // Use a NONE policy, then re-open with ALL and confirm nothing was
        // ever indexed - proves the WRITE side really skipped, not just that
        // the READ side filters.
        try (GraphStore store = open(IndexPolicy.none())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));
        }
        try (GraphStore store = open(IndexPolicy.all())) {
            // Index is empty because the writer never put anything there.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice")).isEmpty();
        }
    }

    @Test
    void shouldCleanUpOnlyIndexedPropertiesOnRemoveVertex() throws RocksDBException {
        IndexPolicy policy = IndexPolicy.builder()
            .indexVertexProperty(PERSON_TYPE, "name")
            .build();
        try (GraphStore store = open(policy)) {
            store.addVertex(new Vertex(1L, PERSON_TYPE)
                .withProperty("name", "Alice").withProperty("age", 30));
            store.removeVertex(PERSON_TYPE, 1L);

            // Indexed entry gone.
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice")).isEmpty();
            // Vertex gone.
            assertThat(store.getVertex(PERSON_TYPE, 1L)).isNull();
        }
    }

    @Test
    void partialUpdateShouldRespectPolicy() throws RocksDBException {
        IndexPolicy policy = IndexPolicy.builder()
            .indexVertexProperty(PERSON_TYPE, "name")
            .build();
        try (GraphStore store = open(policy)) {
            store.addVertex(new Vertex(1L, PERSON_TYPE).withProperty("name", "Alice"));

            // Update an UN-indexed property: succeeds, main store reflects it,
            // index remains untouched (and there was no entry to delete).
            assertThat(store.updateVertexProperty(PERSON_TYPE, 1L, "age", 30)).isTrue();
            assertThat(store.getVertex(PERSON_TYPE, 1L).getProperty("age")).isEqualTo(30);
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "age", 30)).isEmpty();

            // Update the indexed name: index moves Alice -> Bob.
            assertThat(store.updateVertexProperty(PERSON_TYPE, 1L, "name", "Bob")).isTrue();
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Alice")).isEmpty();
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "name", "Bob"))
                .extracting(Vertex::getId).containsExactly(1L);
        }
    }

    // ========================== Built-ins ==========================

    @Test
    void allPolicyIndexesEverything() throws RocksDBException {
        try (GraphStore store = open(IndexPolicy.all())) {
            store.addVertex(new Vertex(1L, PERSON_TYPE)
                .withProperty("anything", "goes").withProperty("at", 42));
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "anything", "goes"))
                .extracting(Vertex::getId).containsExactly(1L);
            assertThat(store.findVerticesByProperty(PERSON_TYPE, "at", 42))
                .extracting(Vertex::getId).containsExactly(1L);
        }
    }
}
