package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.schema;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Vertex;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for schema definition, validation, and schema-aware graph store.
 */
class SchemaTest {

    private static final int PERSON_TYPE = 1;
    private static final int COMPANY_TYPE = 2;
    private static final int KNOWS_TYPE = 10;
    private static final int WORKS_AT_TYPE = 11;

    @BeforeAll
    static void loadLibrary() {
        RocksDB.loadLibrary();
    }

    @TempDir
    Path tempDir;

    private GraphSchema createSocialGraphSchema() {
        return new GraphSchema()
            .registerVertexType(new VertexTypeDef(PERSON_TYPE, "Person")
                .withProperty("name", PropertyType.STRING)
                .withProperty("age", PropertyType.INTEGER))
            .registerVertexType(new VertexTypeDef(COMPANY_TYPE, "Company")
                .withProperty("name", PropertyType.STRING)
                .withProperty("founded", PropertyType.INTEGER))
            .registerEdgeType(new EdgeTypeDef(KNOWS_TYPE, "KNOWS", PERSON_TYPE, PERSON_TYPE)
                .withProperty("since", PropertyType.STRING))
            .registerEdgeType(new EdgeTypeDef(WORKS_AT_TYPE, "WORKS_AT", PERSON_TYPE, COMPANY_TYPE)
                .withProperty("role", PropertyType.STRING)
                .withProperty("salary", PropertyType.LONG));
    }

    // ========================== Schema Definition ==========================

    @Test
    void shouldRegisterVertexAndEdgeTypes() {
        GraphSchema schema = createSocialGraphSchema();

        assertThat(schema.hasVertexType(PERSON_TYPE)).isTrue();
        assertThat(schema.hasVertexType(COMPANY_TYPE)).isTrue();
        assertThat(schema.hasVertexType(999)).isFalse();

        assertThat(schema.hasEdgeType(KNOWS_TYPE)).isTrue();
        assertThat(schema.hasEdgeType(WORKS_AT_TYPE)).isTrue();
        assertThat(schema.hasEdgeType(999)).isFalse();
    }

    @Test
    void shouldRetrieveTypeDefinitions() {
        GraphSchema schema = createSocialGraphSchema();

        VertexTypeDef person = schema.getVertexType(PERSON_TYPE);
        assertThat(person.getName()).isEqualTo("Person");
        assertThat(person.getProperties()).containsKeys("name", "age");
        assertThat(person.getPropertyType("name")).isEqualTo(PropertyType.STRING);
        assertThat(person.getPropertyType("age")).isEqualTo(PropertyType.INTEGER);

        EdgeTypeDef worksAt = schema.getEdgeType(WORKS_AT_TYPE);
        assertThat(worksAt.getName()).isEqualTo("WORKS_AT");
        assertThat(worksAt.getSrcTypeId()).isEqualTo(PERSON_TYPE);
        assertThat(worksAt.getDstTypeId()).isEqualTo(COMPANY_TYPE);
    }

    // ========================== Vertex Validation ==========================

    @Test
    void shouldAcceptValidVertex() {
        GraphSchema schema = createSocialGraphSchema();
        SchemaValidator validator = new SchemaValidator(schema);

        Vertex alice = new Vertex(1L, PERSON_TYPE)
            .withProperty("name", "Alice")
            .withProperty("age", 30);

        validator.validate(alice); // should not throw
    }

    @Test
    void shouldRejectUnknownVertexType() {
        GraphSchema schema = createSocialGraphSchema();
        SchemaValidator validator = new SchemaValidator(schema);

        Vertex unknown = new Vertex(1L, 999).withProperty("name", "X");

        assertThatThrownBy(() -> validator.validate(unknown))
            .isInstanceOf(SchemaValidationException.class)
            .hasMessageContaining("Unknown vertex type");
    }

    @Test
    void shouldRejectUndeclaredProperty() {
        GraphSchema schema = createSocialGraphSchema();
        SchemaValidator validator = new SchemaValidator(schema);

        Vertex alice = new Vertex(1L, PERSON_TYPE)
            .withProperty("name", "Alice")
            .withProperty("email", "alice@example.com"); // not in schema

        assertThatThrownBy(() -> validator.validate(alice))
            .isInstanceOf(SchemaValidationException.class)
            .hasMessageContaining("does not allow property: email");
    }

    @Test
    void shouldRejectWrongPropertyType() {
        GraphSchema schema = createSocialGraphSchema();
        SchemaValidator validator = new SchemaValidator(schema);

        Vertex alice = new Vertex(1L, PERSON_TYPE)
            .withProperty("name", "Alice")
            .withProperty("age", "thirty"); // should be Integer

        assertThatThrownBy(() -> validator.validate(alice))
            .isInstanceOf(SchemaValidationException.class)
            .hasMessageContaining("expects INTEGER but got String");
    }

    // ========================== Edge Validation ==========================

    @Test
    void shouldAcceptValidEdge() {
        GraphSchema schema = createSocialGraphSchema();
        SchemaValidator validator = new SchemaValidator(schema);

        Edge edge = new Edge(1L, 2L, KNOWS_TYPE).withProperty("since", "2020");
        validator.validate(edge); // should not throw
    }

    @Test
    void shouldRejectUnknownEdgeType() {
        GraphSchema schema = createSocialGraphSchema();
        SchemaValidator validator = new SchemaValidator(schema);

        Edge edge = new Edge(1L, 2L, 999);

        assertThatThrownBy(() -> validator.validate(edge))
            .isInstanceOf(SchemaValidationException.class)
            .hasMessageContaining("Unknown edge type");
    }

    @Test
    void shouldRejectWrongEdgePropertyType() {
        GraphSchema schema = createSocialGraphSchema();
        SchemaValidator validator = new SchemaValidator(schema);

        Edge edge = new Edge(1L, 2L, WORKS_AT_TYPE)
            .withProperty("role", "Engineer")
            .withProperty("salary", 50000.5); // should be Long/Integer

        assertThatThrownBy(() -> validator.validate(edge))
            .isInstanceOf(SchemaValidationException.class)
            .hasMessageContaining("expects LONG but got Double");
    }

    // ========================== Endpoint Validation ==========================

    @Test
    void shouldAcceptValidEndpoints() {
        GraphSchema schema = createSocialGraphSchema();
        SchemaValidator validator = new SchemaValidator(schema);

        // Person -> Person is valid for KNOWS
        validator.validateEndpoints(
            new Edge(1L, 2L, KNOWS_TYPE), PERSON_TYPE, PERSON_TYPE);
    }

    @Test
    void shouldRejectInvalidSourceType() {
        GraphSchema schema = createSocialGraphSchema();
        SchemaValidator validator = new SchemaValidator(schema);

        // Company -> Person is invalid for KNOWS (must be Person -> Person)
        assertThatThrownBy(() -> validator.validateEndpoints(
                new Edge(1L, 2L, KNOWS_TYPE), COMPANY_TYPE, PERSON_TYPE))
            .isInstanceOf(SchemaValidationException.class)
            .hasMessageContaining("requires source vertex type " + PERSON_TYPE);
    }

    @Test
    void shouldRejectInvalidDestinationType() {
        GraphSchema schema = createSocialGraphSchema();
        SchemaValidator validator = new SchemaValidator(schema);

        // Person -> Person is invalid for WORKS_AT (must be Person -> Company)
        assertThatThrownBy(() -> validator.validateEndpoints(
                new Edge(1L, 2L, WORKS_AT_TYPE), PERSON_TYPE, PERSON_TYPE))
            .isInstanceOf(SchemaValidationException.class)
            .hasMessageContaining("requires destination vertex type " + COMPANY_TYPE);
    }

    // ========================== Schema-Aware Store Integration ==========================

    @Test
    void shouldStoreValidVerticesAndEdges() throws RocksDBException {
        GraphSchema schema = createSocialGraphSchema();
        SchemaAwareGraphStore store = new SchemaAwareGraphStore(tempDir.toString(), schema);

        long aliceId = store.nextVertexId();
        long bobId = store.nextVertexId();

        store.addVertex(new Vertex(aliceId, PERSON_TYPE)
            .withProperty("name", "Alice")
            .withProperty("age", 30));
        store.addVertex(new Vertex(bobId, PERSON_TYPE)
            .withProperty("name", "Bob")
            .withProperty("age", 25));

        store.addEdge(new Edge(aliceId, bobId, KNOWS_TYPE)
            .withProperty("since", "2020"));

        assertThat(store.getVertex(PERSON_TYPE, aliceId)).isNotNull();
        assertThat(store.getEdge(aliceId, KNOWS_TYPE, bobId)).isNotNull();

        store.close();
    }

    @Test
    void shouldRejectInvalidVertexInStore() throws RocksDBException {
        GraphSchema schema = createSocialGraphSchema();
        SchemaAwareGraphStore store = new SchemaAwareGraphStore(tempDir.toString(), schema);

        Vertex invalid = new Vertex(store.nextVertexId(), PERSON_TYPE)
            .withProperty("name", "Alice")
            .withProperty("email", "alice@example.com"); // undeclared

        assertThatThrownBy(() -> store.addVertex(invalid))
            .isInstanceOf(SchemaValidationException.class)
            .hasMessageContaining("does not allow property: email");

        store.close();
    }

    @Test
    void shouldRejectInvalidEdgeEndpointsInStore() throws RocksDBException {
        GraphSchema schema = createSocialGraphSchema();
        SchemaAwareGraphStore store = new SchemaAwareGraphStore(tempDir.toString(), schema);

        long aliceId = store.nextVertexId();
        long acmeId = store.nextVertexId();

        store.addVertex(new Vertex(aliceId, PERSON_TYPE)
            .withProperty("name", "Alice")
            .withProperty("age", 30));
        store.addVertex(new Vertex(acmeId, COMPANY_TYPE)
            .withProperty("name", "Acme")
            .withProperty("founded", 1990));

        // KNOWS requires Person -> Person, but we're connecting Person -> Company
        Edge invalidEdge = new Edge(aliceId, acmeId, KNOWS_TYPE);

        assertThatThrownBy(() -> store.addEdge(invalidEdge))
            .isInstanceOf(SchemaValidationException.class)
            .hasMessageContaining("requires destination vertex type " + PERSON_TYPE);

        store.close();
    }

    @Test
    void shouldAcceptValidWorksAtEdge() throws RocksDBException {
        GraphSchema schema = createSocialGraphSchema();
        SchemaAwareGraphStore store = new SchemaAwareGraphStore(tempDir.toString(), schema);

        long aliceId = store.nextVertexId();
        long acmeId = store.nextVertexId();

        store.addVertex(new Vertex(aliceId, PERSON_TYPE)
            .withProperty("name", "Alice")
            .withProperty("age", 30));
        store.addVertex(new Vertex(acmeId, COMPANY_TYPE)
            .withProperty("name", "Acme")
            .withProperty("founded", 1990));

        // WORKS_AT requires Person -> Company — this is valid
        Edge validEdge = new Edge(aliceId, acmeId, WORKS_AT_TYPE)
            .withProperty("role", "Engineer")
            .withProperty("salary", 100000L);

        store.addEdge(validEdge);

        assertThat(store.getEdge(aliceId, WORKS_AT_TYPE, acmeId)).isNotNull();

        store.close();
    }

    @Test
    void shouldAllowVertexWithNoProperties() throws RocksDBException {
        GraphSchema schema = createSocialGraphSchema();
        SchemaAwareGraphStore store = new SchemaAwareGraphStore(tempDir.toString(), schema);

        Vertex minimal = new Vertex(store.nextVertexId(), PERSON_TYPE);
        store.addVertex(minimal); // should not throw

        store.close();
    }
}
