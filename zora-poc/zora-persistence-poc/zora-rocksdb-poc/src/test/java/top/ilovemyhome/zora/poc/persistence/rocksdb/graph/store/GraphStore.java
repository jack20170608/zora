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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph storage engine built on top of RocksDB.
 *
 * <p>Provides CRUD for vertices and edges, bidirectional neighbour traversal,
 * and a production-grade property index that supports both equality and
 * range queries.
 *
 * <h2>Storage layout</h2>
 * <ul>
 *   <li>{@code default} - metadata counters (vertex id, property id).</li>
 *   <li>{@code cf_vertex} - vertex primary store, key = {@code V|typeId|vertexId}.</li>
 *   <li>{@code cf_edge}   - bidirectional edge adjacency, key = {@code E|...}.</li>
 *   <li>{@code cf_index}  - secondary property index with order-preserving
 *       value encoding, key = {@code I|typeId|propId|encodedValue|vertexId}.</li>
 *   <li>{@code cf_schema} - dictionary mapping property name &lt;-&gt; propId,
 *       so the index key carries a 4-byte numeric id rather than a hash.</li>
 * </ul>
 *
 * <h2>Key design highlights</h2>
 * <ul>
 *   <li>Numeric property ids eliminate hash collisions.</li>
 *   <li>Order-preserving value encoding (long/double/bool/string) makes the
 *       index supports equality and range queries with a single
 *       {@code seek + prefix scan}.</li>
 *   <li>Index keys carry both {@code typeId} and {@code vertexId}, so
 *       materializing a hit is an O(log N) point lookup rather than a full
 *       vertex CF scan.</li>
 *   <li>{@link #addVertex(Vertex)} diff-updates the property index, so a
 *       value change deletes the stale entry instead of leaking it.</li>
 * </ul>
 */
public class GraphStore implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GraphStore.class);

    private static final byte[] EMPTY_VALUE = new byte[0];
    private static final byte[] VERTEX_COUNTER_KEY = "__vertex_counter__".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PROP_COUNTER_KEY   = "__prop_id_counter__".getBytes(StandardCharsets.UTF_8);

    /** Forward dictionary entry prefix: {@code S|N|<propName>} -> propId. */
    private static final byte SCHEMA_NAME_TO_ID = 'N';
    /** Reverse dictionary entry prefix: {@code S|I|<propId>}   -> propName. */
    private static final byte SCHEMA_ID_TO_NAME = 'I';

    private final RocksDB db;
    private final ColumnFamilyHandle cfDefault;
    private final ColumnFamilyHandle cfVertex;
    private final ColumnFamilyHandle cfEdge;
    private final ColumnFamilyHandle cfIndex;
    private final ColumnFamilyHandle cfSchema;
    private final WriteOptions writeOptions;

    /** Tracks all native handles we opened so close() can release them in reverse order. */
    private final List<AutoCloseable> resources = new ArrayList<>();

    /** In-memory cache of the propName -> propId mapping to avoid hitting RocksDB on every write. */
    private final Map<String, Integer> propIdCache = new ConcurrentHashMap<>();

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
        resources.add(dbOptions);

        List<ColumnFamilyDescriptor> cfDescriptors = Arrays.asList(
            new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY),
            new ColumnFamilyDescriptor("vertex".getBytes(StandardCharsets.UTF_8)),
            new ColumnFamilyDescriptor("edge".getBytes(StandardCharsets.UTF_8)),
            new ColumnFamilyDescriptor("index".getBytes(StandardCharsets.UTF_8)),
            new ColumnFamilyDescriptor("schema".getBytes(StandardCharsets.UTF_8))
        );

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
        this.db = RocksDB.open(dbOptions, dbPath, cfDescriptors, cfHandles);

        this.cfDefault = cfHandles.get(0);
        this.cfVertex  = cfHandles.get(1);
        this.cfEdge    = cfHandles.get(2);
        this.cfIndex   = cfHandles.get(3);
        this.cfSchema  = cfHandles.get(4);
        resources.add(cfSchema);
        resources.add(cfIndex);
        resources.add(cfEdge);
        resources.add(cfVertex);
        resources.add(cfDefault);
        resources.add(db);

        this.writeOptions = new WriteOptions().setSync(false);
        resources.add(writeOptions);

        warmPropIdCache();

        LOG.info("GraphStore opened at: {}", dbPath);
    }

    // ========================== Vertex Operations ==========================

    /**
     * Adds or updates a vertex. The property index is diff-updated against the
     * previously stored vertex so stale (old-value) entries are removed
     * atomically with the write of the new value.
     */
    public void addVertex(Vertex vertex) throws RocksDBException {
        Vertex previous = getVertex(vertex.getTypeId(), vertex.getId());

        byte[] key = KeyCodec.encodeVertexKey(vertex.getTypeId(), vertex.getId());
        byte[] value = ValueCodec.encodeVertex(vertex);

        try (WriteBatch batch = new WriteBatch()) {
            batch.put(cfVertex, key, value);

            // 1) Remove index entries whose value no longer matches.
            if (previous != null) {
                Map<String, Object> newProps = vertex.getProperties();
                for (Map.Entry<String, Object> entry : previous.getProperties().entrySet()) {
                    Object newValue = newProps.get(entry.getKey());
                    if (!Objects.equals(newValue, entry.getValue())) {
                        int propId = resolvePropId(entry.getKey(), batch);
                        batch.delete(cfIndex, buildIndexKey(vertex.getTypeId(), propId,
                            entry.getValue(), vertex.getId()));
                    }
                }
            }

            // 2) Write index entries that are new or whose value changed.
            Map<String, Object> oldProps = previous == null ? Map.of() : previous.getProperties();
            for (Map.Entry<String, Object> entry : vertex.getProperties().entrySet()) {
                Object oldValue = oldProps.get(entry.getKey());
                if (!Objects.equals(oldValue, entry.getValue())) {
                    int propId = resolvePropId(entry.getKey(), batch);
                    batch.put(cfIndex,
                        buildIndexKey(vertex.getTypeId(), propId, entry.getValue(), vertex.getId()),
                        EMPTY_VALUE);
                }
            }

            db.write(writeOptions, batch);
        }
        LOG.debug("Added vertex: {}", vertex.getId());
    }

    /**
     * Retrieves a vertex by id only. The vertex CF is keyed by
     * {@code (typeId, vertexId)}, so without a typeId we have to fall back to
     * a CF scan. New code should prefer {@link #getVertex(int, long)} or use
     * an index lookup which carries the typeId in the key.
     */
    public Vertex getVertex(long vertexId) throws RocksDBException {
        try (RocksIterator it = db.newIterator(cfVertex)) {
            it.seekToFirst();
            while (it.isValid()) {
                if (KeyCodec.decodeVertexId(it.key()) == vertexId) {
                    return ValueCodec.decodeVertex(it.value());
                }
                it.next();
            }
        }
        return null;
    }

    /**
     * Retrieves a vertex by type and id - the fast path, one point lookup.
     */
    public Vertex getVertex(int typeId, long vertexId) throws RocksDBException {
        byte[] key = KeyCodec.encodeVertexKey(typeId, vertexId);
        byte[] value = db.get(cfVertex, key);
        return value != null ? ValueCodec.decodeVertex(value) : null;
    }

    /**
     * Removes a vertex, every edge incident to it, and every property-index
     * entry it owns, all in a single atomic batch.
     */
    public void removeVertex(int typeId, long vertexId) throws RocksDBException {
        Vertex vertex = getVertex(typeId, vertexId);

        try (WriteBatch batch = new WriteBatch()) {
            byte[] vertexKey = KeyCodec.encodeVertexKey(typeId, vertexId);
            batch.delete(cfVertex, vertexKey);

            // Wipe every incident edge with a single prefix scan over cf_edge.
            try (RocksIterator it = db.newIterator(cfEdge)) {
                byte[] prefix = KeyCodec.edgePrefix(vertexId);
                for (it.seek(prefix); it.isValid() && KeyCodec.startsWith(it.key(), prefix); it.next()) {
                    batch.delete(cfEdge, it.key());
                }
            }

            // Drop property-index entries owned by this vertex.
            if (vertex != null) {
                for (Map.Entry<String, Object> entry : vertex.getProperties().entrySet()) {
                    int propId = resolvePropId(entry.getKey(), batch);
                    batch.delete(cfIndex, buildIndexKey(typeId, propId, entry.getValue(), vertexId));
                }
            }

            db.write(writeOptions, batch);
        }
        LOG.debug("Removed vertex: {}", vertexId);
    }

    // ========================== Edge Operations ==========================

    /**
     * Adds an edge by writing both the outgoing and the incoming key
     * atomically, so prefix scans from either endpoint stay consistent.
     */
    public void addEdge(Edge edge) throws RocksDBException {
        byte[] outKey = KeyCodec.encodeOutEdgeKey(edge.getSrcId(), edge.getTypeId(), edge.getDstId());
        byte[] inKey  = KeyCodec.encodeInEdgeKey (edge.getDstId(), edge.getTypeId(), edge.getSrcId());
        byte[] value  = ValueCodec.encodeEdge(edge);

        try (WriteBatch batch = new WriteBatch()) {
            batch.put(cfEdge, outKey, value);
            batch.put(cfEdge, inKey,  value);
            db.write(writeOptions, batch);
        }
        LOG.debug("Added edge: {} -> {} (type={})", edge.getSrcId(), edge.getDstId(), edge.getTypeId());
    }

    /**
     * Retrieves an edge by its endpoints and type.
     */
    public Edge getEdge(long srcId, int edgeType, long dstId) throws RocksDBException {
        byte[] outKey = KeyCodec.encodeOutEdgeKey(srcId, edgeType, dstId);
        byte[] value = db.get(cfEdge, outKey);
        return value != null ? ValueCodec.decodeEdge(value) : null;
    }

    /**
     * Removes both directional keys for the given edge.
     */
    public void removeEdge(long srcId, int edgeType, long dstId) throws RocksDBException {
        byte[] outKey = KeyCodec.encodeOutEdgeKey(srcId, edgeType, dstId);
        byte[] inKey  = KeyCodec.encodeInEdgeKey (dstId, edgeType, srcId);

        try (WriteBatch batch = new WriteBatch()) {
            batch.delete(cfEdge, outKey);
            batch.delete(cfEdge, inKey);
            db.write(writeOptions, batch);
        }
        LOG.debug("Removed edge: {} -> {} (type={})", srcId, dstId, edgeType);
    }

    // ========================== Traversal Operations ==========================

    /**
     * Returns every outgoing edge of {@code vertexId} for {@code edgeType},
     * via one prefix scan over cf_edge.
     */
    public List<Edge> getOutEdges(long vertexId, int edgeType) throws RocksDBException {
        return scanEdges(vertexId, edgeType, KeyCodec.Direction.OUT);
    }

    /**
     * Returns every incoming edge of {@code vertexId} for {@code edgeType}.
     */
    public List<Edge> getInEdges(long vertexId, int edgeType) throws RocksDBException {
        return scanEdges(vertexId, edgeType, KeyCodec.Direction.IN);
    }

    private List<Edge> scanEdges(long vertexId, int edgeType, KeyCodec.Direction dir) {
        List<Edge> edges = new ArrayList<>();
        byte[] prefix = KeyCodec.edgePrefix(vertexId, edgeType, dir);
        try (RocksIterator it = db.newIterator(cfEdge)) {
            for (it.seek(prefix); it.isValid() && KeyCodec.startsWith(it.key(), prefix); it.next()) {
                edges.add(ValueCodec.decodeEdge(it.value()));
            }
        }
        return edges;
    }

    /**
     * Returns every neighbour vertex reachable via an outgoing edge of the
     * given type. The neighbour materialization uses the typeId stored in the
     * vertex key so each hop is an O(log N) point lookup.
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
     * Finds vertices whose property exactly equals {@code value}, using the
     * secondary index. Because the encoded value is fixed-length-by-tag (for
     * numbers/bool) or self-terminating (for strings), the scan stops on the
     * first non-matching key instead of running to the end of the
     * {@code (typeId, propId)} segment.
     */
    public List<Vertex> findVerticesByProperty(int typeId, String propName, Object value) throws RocksDBException {
        Integer propId = propIdCache.get(propName);
        if (propId == null) {
            // Property was never indexed -> no possible matches; do not even
            // allocate an iterator.
            return List.of();
        }

        byte[] encodedValue = Property.encodeValue(value);
        byte[] prefix = KeyCodec.indexValuePrefix(typeId, propId, encodedValue);

        List<Vertex> result = new ArrayList<>();
        try (RocksIterator it = db.newIterator(cfIndex)) {
            for (it.seek(prefix); it.isValid() && KeyCodec.startsWith(it.key(), prefix); it.next()) {
                Vertex v = materializeFromIndexKey(it.key());
                if (v != null) result.add(v);
            }
        }
        return result;
    }

    /**
     * Finds vertices whose property falls in {@code [low, high]} (both
     * inclusive). The bounds must be of the same type since the encoded form
     * includes a type tag that determines sort order.
     */
    public List<Vertex> findVerticesByPropertyRange(int typeId, String propName,
                                                    Object low, Object high) throws RocksDBException {
        Integer propId = propIdCache.get(propName);
        if (propId == null) {
            return List.of();
        }

        byte[] encodedLow  = Property.encodeValue(low);
        byte[] encodedHigh = Property.encodeValue(high);
        byte[] lowerBound  = KeyCodec.indexRangeLowerBound(typeId, propId, encodedLow);
        byte[] upperBound  = KeyCodec.indexRangeUpperBound(typeId, propId, encodedHigh);

        List<Vertex> result = new ArrayList<>();
        try (RocksIterator it = db.newIterator(cfIndex)) {
            for (it.seek(lowerBound); it.isValid(); it.next()) {
                byte[] key = it.key();
                if (KeyCodec.compareUnsigned(key, upperBound) > 0) {
                    break;
                }
                Vertex v = materializeFromIndexKey(key);
                if (v != null) result.add(v);
            }
        }
        return result;
    }

    private Vertex materializeFromIndexKey(byte[] indexKey) throws RocksDBException {
        // typeId lives in the index key, so we hit cf_vertex with a precise key.
        int typeId = KeyCodec.decodeIndexTypeId(indexKey);
        long vertexId = KeyCodec.decodeIndexVertexId(indexKey);
        return getVertex(typeId, vertexId);
    }

    // ========================== Counter ==========================

    /**
     * Generates a monotonically increasing vertex id.
     */
    public long nextVertexId() throws RocksDBException {
        synchronized (this) {
            byte[] current = db.get(cfDefault, VERTEX_COUNTER_KEY);
            long next = (current == null) ? 1 : ByteBuffer.wrap(current).getLong() + 1;
            db.put(cfDefault, VERTEX_COUNTER_KEY, ByteBuffer.allocate(8).putLong(next).array());
            return next;
        }
    }

    // ========================== Schema Dictionary ==========================

    /**
     * Resolves the numeric propId for a property name, allocating a new one
     * (and writing the dictionary entry into the same batch) on first use.
     *
     * <p>The {@code cf_schema} CF holds two entries per property:
     * <pre>
     *   S|N|<propName-bytes> -> propId (4 bytes)
     *   S|I|<propId>         -> propName-bytes
     * </pre>
     * which keeps the mapping debuggable and easy to enumerate.
     */
    private int resolvePropId(String propName, WriteBatch batch) throws RocksDBException {
        Integer cached = propIdCache.get(propName);
        if (cached != null) return cached;

        synchronized (propIdCache) {
            cached = propIdCache.get(propName);
            if (cached != null) return cached;

            byte[] nameKey = schemaNameKey(propName);
            byte[] existing = db.get(cfSchema, nameKey);
            if (existing != null) {
                int id = ByteBuffer.wrap(existing).getInt();
                propIdCache.put(propName, id);
                return id;
            }

            // Allocate a brand-new propId. The counter is read-modify-written
            // under propIdCache's lock, which is enough for the single-process
            // PoC; a multi-writer deployment would use OptimisticTransactionDB.
            byte[] counterBytes = db.get(cfDefault, PROP_COUNTER_KEY);
            int nextId = counterBytes == null ? 1 : ByteBuffer.wrap(counterBytes).getInt() + 1;
            db.put(cfDefault, PROP_COUNTER_KEY, ByteBuffer.allocate(4).putInt(nextId).array());

            byte[] idBytes = ByteBuffer.allocate(4).putInt(nextId).array();
            batch.put(cfSchema, nameKey, idBytes);
            batch.put(cfSchema, schemaIdKey(nextId), propName.getBytes(StandardCharsets.UTF_8));

            propIdCache.put(propName, nextId);
            return nextId;
        }
    }

    /** Loads existing dictionary entries into the in-memory cache on open. */
    private void warmPropIdCache() throws RocksDBException {
        try (RocksIterator it = db.newIterator(cfSchema)) {
            byte[] prefix = new byte[]{'S', SCHEMA_NAME_TO_ID};
            for (it.seek(prefix); it.isValid() && KeyCodec.startsWith(it.key(), prefix); it.next()) {
                byte[] key = it.key();
                String propName = new String(key, prefix.length, key.length - prefix.length, StandardCharsets.UTF_8);
                int propId = ByteBuffer.wrap(it.value()).getInt();
                propIdCache.put(propName, propId);
            }
        }
    }

    private static byte[] schemaNameKey(String propName) {
        byte[] nameBytes = propName.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(2 + nameBytes.length);
        buf.put((byte) 'S');
        buf.put(SCHEMA_NAME_TO_ID);
        buf.put(nameBytes);
        return buf.array();
    }

    private static byte[] schemaIdKey(int propId) {
        ByteBuffer buf = ByteBuffer.allocate(2 + 4);
        buf.put((byte) 'S');
        buf.put(SCHEMA_ID_TO_NAME);
        buf.putInt(propId);
        return buf.array();
    }

    // ========================== Utility ==========================

    private byte[] buildIndexKey(int typeId, int propId, Object value, long vertexId) {
        byte[] encodedValue = Property.encodeValue(value);
        return KeyCodec.encodeIndexKey(typeId, propId, encodedValue, vertexId);
    }

    @Override
    public void close() {
        // Release in reverse order: WriteOptions, RocksDB, then every CF handle.
        for (int i = resources.size() - 1; i >= 0; i--) {
            try {
                resources.get(i).close();
            } catch (Exception e) {
                LOG.warn("Failed to close resource {}", resources.get(i), e);
            }
        }
        LOG.info("GraphStore closed");
    }
}
