package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.schema;

import org.rocksdb.RocksDBException;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Vertex;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.store.GraphStore;

import java.util.List;

/**
 * A schema-aware wrapper around {@link GraphStore} that validates
 * all writes against a {@link GraphSchema} before persisting.
 *
 * <p>This adds type safety to the graph storage layer, ensuring that
 * only vertices and edges conforming to the registered schema are stored.
 */
public class SchemaAwareGraphStore implements AutoCloseable {

    private final GraphStore store;
    private final SchemaValidator validator;

    public SchemaAwareGraphStore(String dbPath, GraphSchema schema) throws RocksDBException {
        this.store = new GraphStore(dbPath);
        this.validator = new SchemaValidator(schema);
    }

    /**
     * Adds a vertex after validating it against the schema.
     *
     * @param vertex the vertex to add
     * @throws SchemaValidationException if the vertex violates the schema
     * @throws RocksDBException          if a database error occurs
     */
    public void addVertex(Vertex vertex) throws RocksDBException {
        validator.validate(vertex);
        store.addVertex(vertex);
    }

    /**
     * Adds an edge after validating it and its endpoints against the schema.
     *
     * @param edge the edge to add
     * @throws SchemaValidationException if the edge violates the schema
     * @throws RocksDBException          if a database error occurs
     */
    public void addEdge(Edge edge) throws RocksDBException {
        validator.validate(edge);

        // Validate endpoint types
        Vertex src = store.getVertex(edge.getSrcId());
        Vertex dst = store.getVertex(edge.getDstId());
        if (src == null) {
            throw new SchemaValidationException(
                "Source vertex not found: " + edge.getSrcId());
        }
        if (dst == null) {
            throw new SchemaValidationException(
                "Destination vertex not found: " + edge.getDstId());
        }
        validator.validateEndpoints(edge, src.getTypeId(), dst.getTypeId());

        store.addEdge(edge);
    }

    // Delegate read operations directly to the underlying store

    public Vertex getVertex(int typeId, long vertexId) throws RocksDBException {
        return store.getVertex(typeId, vertexId);
    }

    public Vertex getVertex(long vertexId) throws RocksDBException {
        return store.getVertex(vertexId);
    }

    public void removeVertex(int typeId, long vertexId) throws RocksDBException {
        store.removeVertex(typeId, vertexId);
    }

    public Edge getEdge(long srcId, int edgeType, long dstId) throws RocksDBException {
        return store.getEdge(srcId, edgeType, dstId);
    }

    public void removeEdge(long srcId, int edgeType, long dstId) throws RocksDBException {
        store.removeEdge(srcId, edgeType, dstId);
    }

    public List<Edge> getOutEdges(long vertexId, int edgeType) throws RocksDBException {
        return store.getOutEdges(vertexId, edgeType);
    }

    public List<Edge> getInEdges(long vertexId, int edgeType) throws RocksDBException {
        return store.getInEdges(vertexId, edgeType);
    }

    public List<Vertex> getNeighbors(long vertexId, int edgeType) throws RocksDBException {
        return store.getNeighbors(vertexId, edgeType);
    }

    public List<Vertex> findVerticesByProperty(int typeId, String propName, Object value) throws RocksDBException {
        return store.findVerticesByProperty(typeId, propName, value);
    }

    public long nextVertexId() throws RocksDBException {
        return store.nextVertexId();
    }

    @Override
    public void close() {
        store.close();
    }
}
