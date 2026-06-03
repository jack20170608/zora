package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.store;

import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.codec.KeyCodec;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.codec.ValueCodec;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Property;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Vertex;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Graph storage engine built on top of RocksDB.
 * Provides CRUD operations for vertices and edges, neighbor traversal,
 * and property-based indexing.
 *
 * Storage layout:
 * - cf_vertex: vertex id -> vertex JSON
 * - cf_edge:   bidirectional edge keys -> edge JSON
 * - cf_index:  property index keys -> empty
 */
public class GraphStore implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GraphStore.class);
    private static final byte[] EMPTY_VALUE = new byte[0];
    private static final byte[] COUNTER_KEY = "__vertex_counter__".getBytes();

    private final RocksDB db;
    private final ColumnFamilyHandle cfVertex;
    private final ColumnFamilyHandle cfEdge;
    private final ColumnFamilyHandle cfIndex;
    private final WriteOptions writeOptions;

    /**
     * Opens or creates a graph store at the given path.
     *
     * @param dbPath the database directory path
     * @throws RocksDBException if the database cannot be opened
     */
    public GraphStore(String dbPath) throws RocksDBException {
        RocksDB.loadLibrary();

        DBOptions dbOptions = new DBOptions();
        dbOptions.setCreateIfMissing(true);
        dbOptions.setCreateMissingColumnFamilies(true);

        List<ColumnFamilyDescriptor> cfDescriptors = Arrays.asList(
            new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY),
            new ColumnFamilyDescriptor("vertex".getBytes()),
            new ColumnFamilyDescriptor("edge".getBytes()),
            new ColumnFamilyDescriptor("index".getBytes())
        );

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
        this.db = RocksDB.open(dbOptions, dbPath, cfDescriptors, cfHandles);

        this.cfVertex = cfHandles.get(1);
        this.cfEdge = cfHandles.get(2);
        this.cfIndex = cfHandles.get(3);

        this.writeOptions = new WriteOptions().setSync(false);

        LOG.info("GraphStore opened at: {}", dbPath);
    }

    // ========================== Vertex Operations ==========================

    /**
     * Adds or updates a vertex in the store.
     * Also updates property indexes if the vertex has properties.
     *
     * @param vertex the vertex to store
     * @throws RocksDBException if a database error occurs
     */
    public void addVertex(Vertex vertex) throws RocksDBException {
        byte[] key = KeyCodec.encodeVertexKey(vertex.getTypeId(), vertex.getId());
        byte[] value = ValueCodec.encodeVertex(vertex);

        WriteBatch batch = new WriteBatch();
        batch.put(cfVertex, key, value);

        // Update property indexes
        for (Map.Entry<String, Object> entry : vertex.getProperties().entrySet()) {
            byte[] indexKey = buildIndexKey(vertex.getTypeId(), entry.getKey(), entry.getValue(), vertex.getId());
            batch.put(cfIndex, indexKey, EMPTY_VALUE);
        }

        db.write(writeOptions, batch);
        LOG.debug("Added vertex: {}", vertex.getId());
    }

    /**
     * Retrieves a vertex by its id.
     *
     * @param vertexId the vertex id
     * @return the vertex, or null if not found
     * @throws RocksDBException if a database error occurs
     */
    public Vertex getVertex(long vertexId) throws RocksDBException {
        // We need typeId to build the key; scan all vertex types for now
        try (RocksIterator it = db.newIterator(cfVertex)) {
            it.seekToFirst();
            while (it.isValid()) {
                long id = KeyCodec.decodeVertexId(it.key());
                if (id == vertexId) {
                    return ValueCodec.decodeVertex(it.value());
                }
                it.next();
            }
        }
        return null;
    }

    /**
     * Retrieves a vertex by type and id.
     *
     * @param typeId   the vertex type id
     * @param vertexId the vertex id
     * @return the vertex, or null if not found
     * @throws RocksDBException if a database error occurs
     */
    public Vertex getVertex(int typeId, long vertexId) throws RocksDBException {
        byte[] key = KeyCodec.encodeVertexKey(typeId, vertexId);
        byte[] value = db.get(cfVertex, key);
        return value != null ? ValueCodec.decodeVertex(value) : null;
    }

    /**
     * Removes a vertex and all its associated edges and indexes.
     *
     * @param typeId   the vertex type id
     * @param vertexId the vertex id
     * @throws RocksDBException if a database error occurs
     */
    public void removeVertex(int typeId, long vertexId) throws RocksDBException {
        WriteBatch batch = new WriteBatch();

        // Remove vertex
        byte[] vertexKey = KeyCodec.encodeVertexKey(typeId, vertexId);
        batch.delete(cfVertex, vertexKey);

        // Remove all edges connected to this vertex (both outgoing and incoming)
        try (RocksIterator it = db.newIterator(cfEdge)) {
            byte[] prefix = KeyCodec.edgePrefix(vertexId);
            it.seek(prefix);
            while (it.isValid() && KeyCodec.startsWith(it.key(), prefix)) {
                batch.delete(cfEdge, it.key());
                it.next();
            }
        }

        // Remove property indexes
        Vertex vertex = getVertex(typeId, vertexId);
        if (vertex != null) {
            for (Map.Entry<String, Object> entry : vertex.getProperties().entrySet()) {
                byte[] indexKey = buildIndexKey(typeId, entry.getKey(), entry.getValue(), vertexId);
                batch.delete(cfIndex, indexKey);
            }
        }

        db.write(writeOptions, batch);
        LOG.debug("Removed vertex: {}", vertexId);
    }

    // ========================== Edge Operations ==========================

    /**
     * Adds an edge to the store. Writes both outgoing and incoming edge keys atomically.
     *
     * @param edge the edge to store
     * @throws RocksDBException if a database error occurs
     */
    public void addEdge(Edge edge) throws RocksDBException {
        byte[] outKey = KeyCodec.encodeOutEdgeKey(edge.getSrcId(), edge.getTypeId(), edge.getDstId());
        byte[] inKey = KeyCodec.encodeInEdgeKey(edge.getDstId(), edge.getTypeId(), edge.getSrcId());
        byte[] value = ValueCodec.encodeEdge(edge);

        WriteBatch batch = new WriteBatch();
        batch.put(cfEdge, outKey, value);
        batch.put(cfEdge, inKey, value);
        db.write(writeOptions, batch);

        LOG.debug("Added edge: {} -> {} (type={})", edge.getSrcId(), edge.getDstId(), edge.getTypeId());
    }

    /**
     * Retrieves an edge by its endpoints and type.
     *
     * @param srcId    the source vertex id
     * @param edgeType the edge type id
     * @param dstId    the destination vertex id
     * @return the edge, or null if not found
     * @throws RocksDBException if a database error occurs
     */
    public Edge getEdge(long srcId, int edgeType, long dstId) throws RocksDBException {
        byte[] outKey = KeyCodec.encodeOutEdgeKey(srcId, edgeType, dstId);
        byte[] value = db.get(cfEdge, outKey);
        return value != null ? ValueCodec.decodeEdge(value) : null;
    }

    /**
     * Removes an edge and both its directional keys.
     *
     * @param srcId    the source vertex id
     * @param edgeType the edge type id
     * @param dstId    the destination vertex id
     * @throws RocksDBException if a database error occurs
     */
    public void removeEdge(long srcId, int edgeType, long dstId) throws RocksDBException {
        byte[] outKey = KeyCodec.encodeOutEdgeKey(srcId, edgeType, dstId);
        byte[] inKey = KeyCodec.encodeInEdgeKey(dstId, edgeType, srcId);

        WriteBatch batch = new WriteBatch();
        batch.delete(cfEdge, outKey);
        batch.delete(cfEdge, inKey);
        db.write(writeOptions, batch);

        LOG.debug("Removed edge: {} -> {} (type={})", srcId, dstId, edgeType);
    }

    // ========================== Traversal Operations ==========================

    /**
     * Gets all outgoing edges of a vertex for a specific edge type.
     *
     * @param vertexId the source vertex id
     * @param edgeType the edge type id
     * @return list of outgoing edges
     * @throws RocksDBException if a database error occurs
     */
    public List<Edge> getOutEdges(long vertexId, int edgeType) throws RocksDBException {
        List<Edge> edges = new ArrayList<>();
        byte[] prefix = KeyCodec.edgePrefix(vertexId, edgeType, KeyCodec.Direction.OUT);

        try (RocksIterator it = db.newIterator(cfEdge)) {
            for (it.seek(prefix); it.isValid() && KeyCodec.startsWith(it.key(), prefix); it.next()) {
                edges.add(ValueCodec.decodeEdge(it.value()));
            }
        }
        return edges;
    }

    /**
     * Gets all incoming edges of a vertex for a specific edge type.
     *
     * @param vertexId the destination vertex id
     * @param edgeType the edge type id
     * @return list of incoming edges
     * @throws RocksDBException if a database error occurs
     */
    public List<Edge> getInEdges(long vertexId, int edgeType) throws RocksDBException {
        List<Edge> edges = new ArrayList<>();
        byte[] prefix = KeyCodec.edgePrefix(vertexId, edgeType, KeyCodec.Direction.IN);

        try (RocksIterator it = db.newIterator(cfEdge)) {
            for (it.seek(prefix); it.isValid() && KeyCodec.startsWith(it.key(), prefix); it.next()) {
                edges.add(ValueCodec.decodeEdge(it.value()));
            }
        }
        return edges;
    }

    /**
     * Gets all neighbor vertices reachable via outgoing edges.
     *
     * @param vertexId the source vertex id
     * @param edgeType the edge type id
     * @return list of neighbor vertices
     * @throws RocksDBException if a database error occurs
     */
    public List<Vertex> getNeighbors(long vertexId, int edgeType) throws RocksDBException {
        List<Vertex> neighbors = new ArrayList<>();
        for (Edge edge : getOutEdges(vertexId, edgeType)) {
            Vertex neighbor = getVertex(edge.getDstId());
            if (neighbor != null) {
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    // ========================== Index Operations ==========================

    /**
     * Finds vertices by property value using the secondary index.
     *
     * @param typeId   the vertex type id
     * @param propName the property name
     * @param value    the property value
     * @return list of matching vertices
     * @throws RocksDBException if a database error occurs
     */
    public List<Vertex> findVerticesByProperty(int typeId, String propName, Object value) throws RocksDBException {
        List<Vertex> result = new ArrayList<>();
        int propHash = Property.hashName(propName);
        byte[] valueBytes = Property.encodeValue(value);
        byte[] prefix = KeyCodec.indexPrefix(typeId, propHash);

        try (RocksIterator it = db.newIterator(cfIndex)) {
            for (it.seek(prefix); it.isValid() && KeyCodec.startsWith(it.key(), prefix); it.next()) {
                // Check if the value matches
                byte[] key = it.key();
                if (valueMatches(key, valueBytes)) {
                    long vertexId = extractVertexIdFromIndexKey(key);
                    Vertex vertex = getVertex(vertexId);
                    if (vertex != null) {
                        result.add(vertex);
                    }
                }
            }
        }
        return result;
    }

    // ========================== Counter ==========================

    /**
     * Generates a monotonically increasing vertex id.
     * Uses a simple counter stored in the default column family.
     *
     * @return the next vertex id
     * @throws RocksDBException if a database error occurs
     */
    public long nextVertexId() throws RocksDBException {
        synchronized (this) {
            byte[] current = db.get(COUNTER_KEY);
            long next = (current == null) ? 1 : ByteBuffer.wrap(current).getLong() + 1;
            db.put(COUNTER_KEY, ByteBuffer.allocate(8).putLong(next).array());
            return next;
        }
    }

    // ========================== Utility ==========================

    private byte[] buildIndexKey(int typeId, String propName, Object value, long vertexId) {
        int propHash = Property.hashName(propName);
        byte[] valueBytes = Property.encodeValue(value);
        return KeyCodec.encodeIndexKey(typeId, propHash, valueBytes, vertexId);
    }

    private boolean valueMatches(byte[] indexKey, byte[] expectedValue) {
        // Key format: I(1) | typeId(4) | propHash(4) | value... | vertexId(8)
        int valueOffset = 1 + 4 + 4;
        int valueLength = indexKey.length - valueOffset - 8;
        if (valueLength != expectedValue.length) {
            return false;
        }
        for (int i = 0; i < valueLength; i++) {
            if (indexKey[valueOffset + i] != expectedValue[i]) {
                return false;
            }
        }
        return true;
    }

    private long extractVertexIdFromIndexKey(byte[] indexKey) {
        return ByteBuffer.wrap(indexKey).getLong(indexKey.length - 8);
    }

    @Override
    public void close() {
        writeOptions.close();
        cfIndex.close();
        cfEdge.close();
        cfVertex.close();
        db.close();
        LOG.info("GraphStore closed");
    }
}
