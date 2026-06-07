package top.ilovemyhome.zora.rocksdb.graph.store;

import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.rocksdb.graph.codec.KeyCodec;
import top.ilovemyhome.zora.rocksdb.graph.codec.ValueCodec;
import top.ilovemyhome.zora.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.rocksdb.graph.model.Property;
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph storage engine built on top of RocksDB {@code TransactionDB}.
 *
 * <p>Provides CRUD for vertices and edges, bidirectional neighbour traversal,
 * and a production-grade property index that supports both equality and
 * range queries over vertex AND edge attributes.
 *
 * <h2>Storage layout</h2>
 * <ul>
 *   <li>{@code default}        - metadata counters (vertex id, property id).</li>
 *   <li>{@code cf_vertex}      - vertex primary store, key = {@code V|typeId|vertexId}.</li>
 *   <li>{@code cf_edge}        - bidirectional edge adjacency, key = {@code E|...}.</li>
 *   <li>{@code cf_index}       - vertex secondary index, key = {@code I|typeId|propId|encVal|vertexId}.</li>
 *   <li>{@code cf_edge_index}  - edge secondary index in three flavours
 *       (global / src-keyed / dst-keyed), key = {@code J|<flavor>|...}.</li>
 *   <li>{@code cf_schema}      - propName &lt;-&gt; propId dictionary, shared by V and E.</li>
 * </ul>
 *
 * <h2>Concurrency model</h2>
 * <p>Every mutator method on {@code GraphStore} ({@link #addVertex},
 * {@link #removeVertex}, {@link #addEdge}, {@link #removeEdge},
 * {@link #nextVertexId}) is a thin wrapper that opens a one-shot
 * {@link GraphTxn}, performs the work and commits. That's enough atomicity
 * for "single store op = single transaction" use cases.
 *
 * <p>If you need to read-modify-write across multiple operations (e.g.
 * "load vertex, merge a field, save it back" without losing concurrent
 * mutations), use {@link #beginTransaction()} to obtain an explicit handle
 * and drive several calls inside one transaction:
 *
 * <pre>{@code
 *   try (GraphTxn t = store.beginTransaction()) {
 *       Vertex v = t.getVertex(personType, 1L);  // pessimistic write-lock
 *       t.addVertex(v.withProperty("marker", true));
 *       t.commit();
 *   }
 * }</pre>
 *
 * <p>Index queries ({@code findVerticesByProperty},
 * {@code findEdgesByProperty}, ...) stay on {@code GraphStore} and run
 * lock-free against the latest committed snapshot - they are not exposed on
 * the transaction handle because iterator-style locking would cover entire
 * index ranges and turn into a contention hotspot.
 */
public class GraphStore implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GraphStore.class);

    private static final byte SCHEMA_NAME_TO_ID = 'N';

    private final TransactionDB db;
    private final ColumnFamilyHandle cfDefault;
    private final ColumnFamilyHandle cfVertex;
    private final ColumnFamilyHandle cfVertexType;
    private final ColumnFamilyHandle cfEdge;
    private final ColumnFamilyHandle cfIndex;
    private final ColumnFamilyHandle cfEdgeIndex;
    private final ColumnFamilyHandle cfSchema;
    private final WriteOptions writeOptions;
    private final ReadOptions readOptions;
    private final TransactionOptions txnOptions;

    /** Tracks all native handles we opened so close() can release them in reverse order. */
    private final List<AutoCloseable> resources = new ArrayList<>();

    /** In-memory cache of the propName -> propId mapping to avoid hitting RocksDB on every write. */
    private final Map<String, Integer> propIdCache = new ConcurrentHashMap<>();

    /**
     * Opens or creates a graph store at the given path with default options.
     * Equivalent to {@code new GraphStore(dbPath, GraphStoreOptions.defaults())}.
     */
    public GraphStore(String dbPath) throws RocksDBException {
        this(dbPath, GraphStoreOptions.defaults());
    }

    /**
     * Opens or creates a graph store at the given path with the supplied
     * tunables.
     *
     * @param dbPath  the database directory path
     * @param options runtime tunables (lock timeout, deadlock detection,
     *                fsync-on-commit). Use {@link GraphStoreOptions#defaults()}
     *                if you have no special requirements.
     * @throws RocksDBException if the database cannot be opened
     */
    public GraphStore(String dbPath, GraphStoreOptions options) throws RocksDBException {
        RocksDB.loadLibrary();

        DBOptions dbOptions = new DBOptions();
        dbOptions.setCreateIfMissing(true);
        dbOptions.setCreateMissingColumnFamilies(true);
        resources.add(dbOptions);

        TransactionDBOptions txnDbOptions = new TransactionDBOptions();
        txnDbOptions.setTransactionLockTimeout(options.lockTimeoutMillis());
        resources.add(txnDbOptions);

        List<ColumnFamilyDescriptor> cfDescriptors = Arrays.asList(
            new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY),
            new ColumnFamilyDescriptor("vertex".getBytes(StandardCharsets.UTF_8)),
            new ColumnFamilyDescriptor("vertex_type".getBytes(StandardCharsets.UTF_8)),
            new ColumnFamilyDescriptor("edge".getBytes(StandardCharsets.UTF_8)),
            new ColumnFamilyDescriptor("index".getBytes(StandardCharsets.UTF_8)),
            new ColumnFamilyDescriptor("edge_index".getBytes(StandardCharsets.UTF_8)),
            new ColumnFamilyDescriptor("schema".getBytes(StandardCharsets.UTF_8))
        );

        List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
        this.db = TransactionDB.open(dbOptions, txnDbOptions, dbPath, cfDescriptors, cfHandles);

        this.cfDefault    = cfHandles.get(0);
        this.cfVertex     = cfHandles.get(1);
        this.cfVertexType = cfHandles.get(2);
        this.cfEdge       = cfHandles.get(3);
        this.cfIndex      = cfHandles.get(4);
        this.cfEdgeIndex  = cfHandles.get(5);
        this.cfSchema     = cfHandles.get(6);
        resources.add(cfSchema);
        resources.add(cfEdgeIndex);
        resources.add(cfIndex);
        resources.add(cfEdge);
        resources.add(cfVertexType);
        resources.add(cfVertex);
        resources.add(cfDefault);
        resources.add(db);

        this.writeOptions = new WriteOptions().setSync(options.syncWrites());
        resources.add(writeOptions);
        this.readOptions = new ReadOptions();
        resources.add(readOptions);
        this.txnOptions = new TransactionOptions().setDeadlockDetect(options.deadlockDetect());
        resources.add(txnOptions);

        warmPropIdCache();

        LOG.info("GraphStore opened at: {}", dbPath);
    }

    // ========================== Transaction Lifecycle ==========================

    /**
     * Opens an explicit graph-level transaction. Always pair with
     * try-with-resources; forgetting {@code commit()} triggers an implicit
     * rollback (and a warning) on close.
     */
    public GraphTxn beginTransaction() {
        Transaction txn = db.beginTransaction(writeOptions, txnOptions);
        return new GraphTxn(this, txn, readOptions);
    }

    // ========================== Vertex Operations ==========================

    /**
     * One-shot {@code addVertex}: opens an internal transaction, runs the
     * upsert, commits. Equivalent to:
     * <pre>try (GraphTxn t = beginTransaction()) { t.addVertex(v); t.commit(); }</pre>
     * Use {@link #beginTransaction()} if you need to bundle multiple ops.
     */
    public void addVertex(Vertex vertex) throws RocksDBException {
        try (GraphTxn t = beginTransaction()) {
            t.addVertex(vertex);
            t.commit();
        }
    }

    /**
     * Retrieves a vertex by id only. Tries the {@code cf_vertex_type}
     * reverse index for an O(log N) point lookup; falls back to a full
     * {@code cf_vertex} scan if (and only if) the reverse entry is missing
     * - which can only happen for legacy vertices written before the
     * reverse index existed.
     *
     * <p>For new code, prefer {@link #getVertex(int, long)} when the typeId
     * is known. Inside an explicit transaction use {@link GraphTxn#getVertex}
     * to additionally hold a write-lock.
     */
    public Vertex getVertex(long vertexId) throws RocksDBException {
        byte[] typeBytes = db.get(cfVertexType, vertexIdToKey(vertexId));
        if (typeBytes != null) {
            int typeId = ByteBuffer.wrap(typeBytes).getInt();
            return getVertex(typeId, vertexId);
        }
        // Fallback for vertices written before the reverse index existed.
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
     * Encodes a vertexId as the 8-byte big-endian key used in cf_vertex_type.
     * Package-private so GraphTxn can share the same encoding.
     */
    static byte[] vertexIdToKey(long vertexId) {
        return ByteBuffer.allocate(8).putLong(vertexId).array();
    }

    /**
     * Retrieves a vertex by type and id - the fast path, one point lookup.
     *
     * <p>Lock-free. Inside a transaction prefer {@link GraphTxn#getVertex}
     * which adds a pessimistic write-lock so a subsequent addVertex in the
     * same txn cannot lose updates.
     */
    public Vertex getVertex(int typeId, long vertexId) throws RocksDBException {
        byte[] key = KeyCodec.encodeVertexKey(typeId, vertexId);
        byte[] value = db.get(cfVertex, key);
        return value != null ? ValueCodec.decodeVertex(value) : null;
    }

    public void removeVertex(int typeId, long vertexId) throws RocksDBException {
        try (GraphTxn t = beginTransaction()) {
            t.removeVertex(typeId, vertexId);
            t.commit();
        }
    }

    // ========================== Edge Operations ==========================

    public void addEdge(Edge edge) throws RocksDBException {
        try (GraphTxn t = beginTransaction()) {
            t.addEdge(edge);
            t.commit();
        }
    }

    public Edge getEdge(long srcId, int edgeType, long dstId) throws RocksDBException {
        byte[] outKey = KeyCodec.encodeOutEdgeKey(srcId, edgeType, dstId);
        byte[] value = db.get(cfEdge, outKey);
        return value != null ? ValueCodec.decodeEdge(value) : null;
    }

    public void removeEdge(long srcId, int edgeType, long dstId) throws RocksDBException {
        try (GraphTxn t = beginTransaction()) {
            t.removeEdge(srcId, edgeType, dstId);
            t.commit();
        }
    }

    // ========================== Traversal Operations ==========================

    public List<Edge> getOutEdges(long vertexId, int edgeType) throws RocksDBException {
        return scanEdges(vertexId, edgeType, KeyCodec.Direction.OUT);
    }

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
     * given type.
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

    // ========================== Vertex Index Queries ==========================

    /**
     * Finds vertices whose property exactly equals {@code value}, using the
     * secondary index. Lock-free; uses the latest committed snapshot.
     */
    public List<Vertex> findVerticesByProperty(int typeId, String propName, Object value) throws RocksDBException {
        Integer propId = propIdCache.get(propName);
        if (propId == null) {
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
     * inclusive). The bounds must be of the same physical type.
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
        int typeId = KeyCodec.decodeIndexTypeId(indexKey);
        long vertexId = KeyCodec.decodeIndexVertexId(indexKey);
        return getVertex(typeId, vertexId);
    }

    // ========================== Edge Index Queries ==========================

    public List<Edge> findEdgesByProperty(int edgeType, String propName, Object value) throws RocksDBException {
        Integer propId = propIdCache.get(propName);
        if (propId == null) return List.of();

        byte[] encVal = Property.encodeValue(value);
        byte[] prefix = KeyCodec.edgeIndexGlobalValuePrefix(edgeType, propId, encVal);

        List<Edge> out = new ArrayList<>();
        try (RocksIterator it = db.newIterator(cfEdgeIndex)) {
            for (it.seek(prefix); it.isValid() && KeyCodec.startsWith(it.key(), prefix); it.next()) {
                long srcId = KeyCodec.decodeEdgeIndexGlobalSrc(it.key());
                long dstId = KeyCodec.decodeEdgeIndexGlobalDst(it.key());
                Edge e = getEdge(srcId, edgeType, dstId);
                if (e != null) out.add(e);
            }
        }
        return out;
    }

    public List<Edge> findEdgesByPropertyRange(int edgeType, String propName,
                                               Object low, Object high) throws RocksDBException {
        Integer propId = propIdCache.get(propName);
        if (propId == null) return List.of();

        byte[] lo = KeyCodec.edgeIndexGlobalRangeLowerBound(edgeType, propId, Property.encodeValue(low));
        byte[] hi = KeyCodec.edgeIndexGlobalRangeUpperBound(edgeType, propId, Property.encodeValue(high));

        List<Edge> out = new ArrayList<>();
        try (RocksIterator it = db.newIterator(cfEdgeIndex)) {
            for (it.seek(lo); it.isValid(); it.next()) {
                byte[] key = it.key();
                if (KeyCodec.compareUnsigned(key, hi) > 0) break;
                long srcId = KeyCodec.decodeEdgeIndexGlobalSrc(key);
                long dstId = KeyCodec.decodeEdgeIndexGlobalDst(key);
                Edge e = getEdge(srcId, edgeType, dstId);
                if (e != null) out.add(e);
            }
        }
        return out;
    }

    public List<Edge> findOutEdgesByProperty(long srcId, int edgeType,
                                             String propName, Object value) throws RocksDBException {
        return findEdgesByEndpointAndProperty(true, srcId, edgeType, propName, value);
    }

    public List<Edge> findInEdgesByProperty(long dstId, int edgeType,
                                            String propName, Object value) throws RocksDBException {
        return findEdgesByEndpointAndProperty(false, dstId, edgeType, propName, value);
    }

    public List<Edge> findOutEdgesByPropertyRange(long srcId, int edgeType, String propName,
                                                  Object low, Object high) throws RocksDBException {
        return findEdgesByEndpointAndPropertyRange(true, srcId, edgeType, propName, low, high);
    }

    public List<Edge> findInEdgesByPropertyRange(long dstId, int edgeType, String propName,
                                                 Object low, Object high) throws RocksDBException {
        return findEdgesByEndpointAndPropertyRange(false, dstId, edgeType, propName, low, high);
    }

    private List<Edge> findEdgesByEndpointAndProperty(boolean srcSide, long endpointId,
                                                      int edgeType, String propName,
                                                      Object value) throws RocksDBException {
        Integer propId = propIdCache.get(propName);
        if (propId == null) return List.of();

        byte[] encVal = Property.encodeValue(value);
        byte[] prefix = KeyCodec.edgeIndexEndpointValuePrefix(srcSide, endpointId, edgeType, propId, encVal);

        List<Edge> out = new ArrayList<>();
        try (RocksIterator it = db.newIterator(cfEdgeIndex)) {
            for (it.seek(prefix); it.isValid() && KeyCodec.startsWith(it.key(), prefix); it.next()) {
                long other = KeyCodec.decodeEdgeIndexEndpointOther(it.key());
                long srcId = srcSide ? endpointId : other;
                long dstId = srcSide ? other      : endpointId;
                Edge e = getEdge(srcId, edgeType, dstId);
                if (e != null) out.add(e);
            }
        }
        return out;
    }

    private List<Edge> findEdgesByEndpointAndPropertyRange(boolean srcSide, long endpointId,
                                                           int edgeType, String propName,
                                                           Object low, Object high) throws RocksDBException {
        Integer propId = propIdCache.get(propName);
        if (propId == null) return List.of();

        byte[] lo = KeyCodec.edgeIndexEndpointRangeLowerBound(
            srcSide, endpointId, edgeType, propId, Property.encodeValue(low));
        byte[] hi = KeyCodec.edgeIndexEndpointRangeUpperBound(
            srcSide, endpointId, edgeType, propId, Property.encodeValue(high));

        List<Edge> out = new ArrayList<>();
        try (RocksIterator it = db.newIterator(cfEdgeIndex)) {
            for (it.seek(lo); it.isValid(); it.next()) {
                byte[] key = it.key();
                if (KeyCodec.compareUnsigned(key, hi) > 0) break;
                long other = KeyCodec.decodeEdgeIndexEndpointOther(key);
                long srcId = srcSide ? endpointId : other;
                long dstId = srcSide ? other      : endpointId;
                Edge e = getEdge(srcId, edgeType, dstId);
                if (e != null) out.add(e);
            }
        }
        return out;
    }

    // ========================== Counter ==========================

    /**
     * One-shot {@code nextVertexId}. For multi-step allocations that should
     * roll back together, use {@link GraphTxn#nextVertexId()} inside an
     * explicit transaction.
     */
    public long nextVertexId() throws RocksDBException {
        try (GraphTxn t = beginTransaction()) {
            long id = t.nextVertexId();
            t.commit();
            return id;
        }
    }

    // ========================== Schema Dictionary (warm-up) ==========================

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

    // ========================== Package-private accessors for GraphTxn ==========================
    //
    // GraphTxn lives in the same package and needs the CF handles, the prop
    // cache, and a few helpers to operate against this store's RocksDB
    // instance. They are intentionally NOT public - external code should
    // go through GraphStore / GraphTxn's documented API.

    ColumnFamilyHandle cfDefault()    { return cfDefault; }
    ColumnFamilyHandle cfVertex()     { return cfVertex; }
    ColumnFamilyHandle cfVertexType() { return cfVertexType; }
    ColumnFamilyHandle cfEdge()       { return cfEdge; }
    ColumnFamilyHandle cfIndex()      { return cfIndex; }
    ColumnFamilyHandle cfEdgeIndex()  { return cfEdgeIndex; }
    ColumnFamilyHandle cfSchema()     { return cfSchema; }
    Map<String, Integer> propIdCache() { return propIdCache; }

    @Override
    public void close() {
        // Release in reverse order: ReadOptions, WriteOptions, DB, then every CF handle.
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
