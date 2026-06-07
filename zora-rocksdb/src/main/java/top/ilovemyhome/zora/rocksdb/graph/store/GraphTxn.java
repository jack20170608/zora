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
import java.util.Map;
import java.util.Objects;

/**
 * Explicit graph-level transaction handle.
 *
 * <p>A {@code GraphTxn} wraps one RocksDB {@link Transaction} and exposes
 * graph-shaped read/write operations on top of it. Use it whenever you need
 * a multi-step read-modify-write to commit atomically:
 *
 * <pre>{@code
 *   try (GraphTxn t = store.beginTransaction()) {
 *       Vertex v = t.getVertex(personType, 1L);     // locks the row
 *       Vertex merged = v.withProperty("marker", true);
 *       t.addVertex(merged);                        // writes inside the txn
 *       t.commit();
 *   }
 * }</pre>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>Every {@code getVertex / getEdge} grabs a pessimistic write-lock on
 *       the underlying row via {@code getForUpdate}. Two transactions
 *       touching the same key serialise on that lock.</li>
 *   <li>Subsequent reads inside the same transaction observe its own pending
 *       writes (read-your-own-writes).</li>
 *   <li>Index lookups ({@code findVerticesByProperty}, etc.) are NOT exposed
 *       on the transaction handle on purpose - they would lock entire index
 *       ranges and become a contention hotspot. Call them on the parent
 *       {@link GraphStore} for lock-free reads against the latest committed
 *       snapshot.</li>
 *   <li>{@link #close()} without a prior {@link #commit()} or
 *       {@link #rollback()} call rolls back implicitly and logs a warning,
 *       so accidentally missing a {@code commit()} inside try-with-resources
 *       cannot leave a transaction dangling.</li>
 * </ul>
 *
 * <p>This handle is NOT thread-safe. Use one {@code GraphTxn} per thread.
 */
public class GraphTxn implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GraphTxn.class);

    private static final byte[] EMPTY_VALUE = new byte[0];
    private static final byte[] VERTEX_COUNTER_KEY = "__vertex_counter__".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PROP_COUNTER_KEY   = "__prop_id_counter__".getBytes(StandardCharsets.UTF_8);

    private static final byte SCHEMA_NAME_TO_ID = 'N';
    private static final byte SCHEMA_ID_TO_NAME = 'I';

    /** The owning store; we borrow its CF handles, codec helpers and propId cache. */
    private final GraphStore store;
    private final Transaction txn;
    private final ReadOptions readOptions;

    /** Tracks whether commit/rollback has already been called. */
    private enum State { OPEN, COMMITTED, ROLLED_BACK }
    private State state = State.OPEN;

    GraphTxn(GraphStore store, Transaction txn, ReadOptions readOptions) {
        this.store = store;
        this.txn = txn;
        this.readOptions = readOptions;
    }

    // ========================== Lifecycle ==========================

    /**
     * Commits the transaction. After this returns the handle is inert; a
     * subsequent {@link #close()} is a no-op.
     */
    public void commit() throws RocksDBException {
        ensureOpen();
        txn.commit();
        state = State.COMMITTED;
    }

    /**
     * Rolls back every pending change. Safe to call before {@link #close()}
     * if the caller decides not to commit.
     */
    public void rollback() throws RocksDBException {
        ensureOpen();
        txn.rollback();
        state = State.ROLLED_BACK;
    }

    @Override
    public void close() {
        if (state == State.OPEN) {
            // Implicit safety net: caller forgot to commit or rollback.
            LOG.warn("GraphTxn closed without commit() or rollback() - rolling back implicitly");
            try {
                txn.rollback();
            } catch (RocksDBException e) {
                LOG.warn("Implicit rollback failed", e);
            }
            state = State.ROLLED_BACK;
        }
        txn.close();
    }

    private void ensureOpen() {
        if (state != State.OPEN) {
            throw new IllegalStateException("GraphTxn is " + state + ", no further operations allowed");
        }
    }

    // ========================== Vertex Operations ==========================

    /**
     * Reads a vertex by id under a write lock - subsequent {@link #addVertex}
     * inside the same transaction will see this lock and prevent concurrent
     * writers from racing it.
     *
     * <p>Returns {@code null} if no vertex with that (typeId, id) exists.
     */
    public Vertex getVertex(int typeId, long vertexId) throws RocksDBException {
        ensureOpen();
        byte[] key = KeyCodec.encodeVertexKey(typeId, vertexId);
        byte[] value = txn.getForUpdate(readOptions, store.cfVertex(), key, true);
        return value != null ? ValueCodec.decodeVertex(value) : null;
    }

    /**
     * Upserts a vertex, diff-updating the property index against the
     * previously committed (or pending-in-this-txn) version, and maintains
     * the vertexId -> typeId reverse index.
     */
    public void addVertex(Vertex vertex) throws RocksDBException {
        ensureOpen();
        byte[] key = KeyCodec.encodeVertexKey(vertex.getTypeId(), vertex.getId());
        byte[] prevBytes = txn.getForUpdate(readOptions, store.cfVertex(), key, true);
        Vertex previous = prevBytes != null ? ValueCodec.decodeVertex(prevBytes) : null;

        txn.put(store.cfVertex(), key, ValueCodec.encodeVertex(vertex));

        // Maintain the vertexId -> typeId reverse index. We only write when
        // it's a fresh insert (previous == null) - on subsequent updates the
        // typeId can't change (same primary key) so the reverse entry is
        // already correct and skipping the put avoids a hot-row write.
        if (previous == null) {
            txn.put(store.cfVertexType(),
                GraphStore.vertexIdToKey(vertex.getId()),
                ByteBuffer.allocate(4).putInt(vertex.getTypeId()).array());
        }

        // 1) Remove index entries whose value no longer matches.
        if (previous != null) {
            Map<String, Object> newProps = vertex.getProperties();
            for (Map.Entry<String, Object> entry : previous.getProperties().entrySet()) {
                Object newValue = newProps.get(entry.getKey());
                if (!Objects.equals(newValue, entry.getValue())) {
                    int propId = resolvePropId(entry.getKey());
                    txn.delete(store.cfIndex(),
                        buildVertexIndexKey(vertex.getTypeId(), propId, entry.getValue(), vertex.getId()));
                }
            }
        }

        // 2) Write index entries that are new or whose value changed.
        Map<String, Object> oldProps = previous == null ? Map.of() : previous.getProperties();
        for (Map.Entry<String, Object> entry : vertex.getProperties().entrySet()) {
            Object oldValue = oldProps.get(entry.getKey());
            if (!Objects.equals(oldValue, entry.getValue())) {
                int propId = resolvePropId(entry.getKey());
                txn.put(store.cfIndex(),
                    buildVertexIndexKey(vertex.getTypeId(), propId, entry.getValue(), vertex.getId()),
                    EMPTY_VALUE);
            }
        }
    }

    /**
     * Removes a vertex, every incident edge and every owned property-index
     * entry, all under the same transaction.
     */
    public void removeVertex(int typeId, long vertexId) throws RocksDBException {
        ensureOpen();
        byte[] vertexKey = KeyCodec.encodeVertexKey(typeId, vertexId);
        byte[] prevBytes = txn.getForUpdate(readOptions, store.cfVertex(), vertexKey, true);
        Vertex vertex = prevBytes != null ? ValueCodec.decodeVertex(prevBytes) : null;
        txn.delete(store.cfVertex(), vertexKey);

        // Drop the vertexId -> typeId reverse-index entry too.
        txn.delete(store.cfVertexType(), GraphStore.vertexIdToKey(vertexId));

        // Wipe every incident edge with one prefix scan over cf_edge, locking
        // each row so concurrent addEdge can't recreate it underneath us.
        try (RocksIterator it = txn.getIterator(readOptions, store.cfEdge())) {
            byte[] prefix = KeyCodec.edgePrefix(vertexId);
            for (it.seek(prefix); it.isValid() && KeyCodec.startsWith(it.key(), prefix); it.next()) {
                byte[] edgeKey = it.key();
                txn.getForUpdate(readOptions, store.cfEdge(), edgeKey, true);
                Edge edge = ValueCodec.decodeEdge(it.value());
                txn.delete(store.cfEdge(), edgeKey);
                deleteEdgeIndexEntries(edge);
            }
        }

        if (vertex != null) {
            for (Map.Entry<String, Object> entry : vertex.getProperties().entrySet()) {
                int propId = resolvePropId(entry.getKey());
                txn.delete(store.cfIndex(),
                    buildVertexIndexKey(typeId, propId, entry.getValue(), vertexId));
            }
        }
    }

    // ========================== Edge Operations ==========================

    /**
     * Reads an edge under a write lock on both directional adjacency rows.
     * Returns {@code null} if no such edge exists.
     */
    public Edge getEdge(long srcId, int edgeType, long dstId) throws RocksDBException {
        ensureOpen();
        byte[] outKey = KeyCodec.encodeOutEdgeKey(srcId, edgeType, dstId);
        byte[] inKey  = KeyCodec.encodeInEdgeKey (dstId, edgeType, srcId);
        byte[] value = txn.getForUpdate(readOptions, store.cfEdge(), outKey, true);
        txn.getForUpdate(readOptions, store.cfEdge(), inKey, true);
        return value != null ? ValueCodec.decodeEdge(value) : null;
    }

    /**
     * Upserts an edge. Writes both directional adjacency keys and 3-way
     * property index entries, diff-ing against the previous version.
     */
    public void addEdge(Edge edge) throws RocksDBException {
        ensureOpen();
        byte[] outKey = KeyCodec.encodeOutEdgeKey(edge.getSrcId(), edge.getTypeId(), edge.getDstId());
        byte[] inKey  = KeyCodec.encodeInEdgeKey (edge.getDstId(), edge.getTypeId(), edge.getSrcId());

        byte[] prevBytes = txn.getForUpdate(readOptions, store.cfEdge(), outKey, true);
        txn.getForUpdate(readOptions, store.cfEdge(), inKey, true);
        Edge previous = prevBytes != null ? ValueCodec.decodeEdge(prevBytes) : null;

        byte[] value = ValueCodec.encodeEdge(edge);
        txn.put(store.cfEdge(), outKey, value);
        txn.put(store.cfEdge(), inKey,  value);

        if (previous != null) {
            Map<String, Object> newProps = edge.getProperties();
            for (Map.Entry<String, Object> entry : previous.getProperties().entrySet()) {
                Object newValue = newProps.get(entry.getKey());
                if (!Objects.equals(newValue, entry.getValue())) {
                    deleteEdgeIndexForProperty(edge, entry.getKey(), entry.getValue());
                }
            }
        }

        Map<String, Object> oldProps = previous == null ? Map.of() : previous.getProperties();
        for (Map.Entry<String, Object> entry : edge.getProperties().entrySet()) {
            Object oldValue = oldProps.get(entry.getKey());
            if (!Objects.equals(oldValue, entry.getValue())) {
                putEdgeIndexForProperty(edge, entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Removes an edge, both adjacency keys, and every owned 3-way index entry.
     */
    public void removeEdge(long srcId, int edgeType, long dstId) throws RocksDBException {
        ensureOpen();
        byte[] outKey = KeyCodec.encodeOutEdgeKey(srcId, edgeType, dstId);
        byte[] inKey  = KeyCodec.encodeInEdgeKey (dstId, edgeType, srcId);

        byte[] prevBytes = txn.getForUpdate(readOptions, store.cfEdge(), outKey, true);
        txn.getForUpdate(readOptions, store.cfEdge(), inKey, true);
        Edge existing = prevBytes != null ? ValueCodec.decodeEdge(prevBytes) : null;

        txn.delete(store.cfEdge(), outKey);
        txn.delete(store.cfEdge(), inKey);
        if (existing != null) {
            deleteEdgeIndexEntries(existing);
        }
    }

    // ========================== Counter ==========================

    /**
     * Allocates the next vertex id from the persistent counter. Lives inside
     * THIS transaction, so a rollback releases the id slot - keep that in
     * mind if you mix nextVertexId() and other mutators in one txn.
     */
    public long nextVertexId() throws RocksDBException {
        ensureOpen();
        byte[] current = txn.getForUpdate(readOptions, store.cfDefault(), VERTEX_COUNTER_KEY, true);
        long next = current == null ? 1 : ByteBuffer.wrap(current).getLong() + 1;
        txn.put(store.cfDefault(), VERTEX_COUNTER_KEY, ByteBuffer.allocate(8).putLong(next).array());
        return next;
    }

    // ========================== Schema Dictionary ==========================

    private int resolvePropId(String propName) throws RocksDBException {
        Integer cached = store.propIdCache().get(propName);
        if (cached != null) return cached;

        byte[] nameKey = schemaNameKey(propName);
        byte[] existing = txn.getForUpdate(readOptions, store.cfSchema(), nameKey, true);
        if (existing != null) {
            int id = ByteBuffer.wrap(existing).getInt();
            store.propIdCache().put(propName, id);
            return id;
        }

        byte[] counterBytes = txn.getForUpdate(readOptions, store.cfDefault(), PROP_COUNTER_KEY, true);
        int nextId = counterBytes == null ? 1 : ByteBuffer.wrap(counterBytes).getInt() + 1;
        txn.put(store.cfDefault(), PROP_COUNTER_KEY, ByteBuffer.allocate(4).putInt(nextId).array());

        byte[] idBytes = ByteBuffer.allocate(4).putInt(nextId).array();
        txn.put(store.cfSchema(), nameKey, idBytes);
        txn.put(store.cfSchema(), schemaIdKey(nextId), propName.getBytes(StandardCharsets.UTF_8));

        store.propIdCache().put(propName, nextId);
        return nextId;
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

    // ========================== Edge Index Helpers ==========================

    private void putEdgeIndexForProperty(Edge edge, String propName, Object value) throws RocksDBException {
        int propId = resolvePropId(propName);
        byte[] encVal = Property.encodeValue(value);
        long srcId = edge.getSrcId();
        long dstId = edge.getDstId();
        int eType = edge.getTypeId();

        txn.put(store.cfEdgeIndex(),
            KeyCodec.encodeEdgeIndexGlobalKey(eType, propId, encVal, srcId, dstId), EMPTY_VALUE);
        txn.put(store.cfEdgeIndex(),
            KeyCodec.encodeEdgeIndexEndpointKey(true,  srcId, eType, propId, encVal, dstId), EMPTY_VALUE);
        txn.put(store.cfEdgeIndex(),
            KeyCodec.encodeEdgeIndexEndpointKey(false, dstId, eType, propId, encVal, srcId), EMPTY_VALUE);
    }

    private void deleteEdgeIndexForProperty(Edge edge, String propName, Object value) throws RocksDBException {
        int propId = resolvePropId(propName);
        byte[] encVal = Property.encodeValue(value);
        long srcId = edge.getSrcId();
        long dstId = edge.getDstId();
        int eType = edge.getTypeId();

        txn.delete(store.cfEdgeIndex(),
            KeyCodec.encodeEdgeIndexGlobalKey(eType, propId, encVal, srcId, dstId));
        txn.delete(store.cfEdgeIndex(),
            KeyCodec.encodeEdgeIndexEndpointKey(true,  srcId, eType, propId, encVal, dstId));
        txn.delete(store.cfEdgeIndex(),
            KeyCodec.encodeEdgeIndexEndpointKey(false, dstId, eType, propId, encVal, srcId));
    }

    private void deleteEdgeIndexEntries(Edge edge) throws RocksDBException {
        for (Map.Entry<String, Object> entry : edge.getProperties().entrySet()) {
            deleteEdgeIndexForProperty(edge, entry.getKey(), entry.getValue());
        }
    }

    // ========================== Misc ==========================

    private static byte[] buildVertexIndexKey(int typeId, int propId, Object value, long vertexId) {
        byte[] encodedValue = Property.encodeValue(value);
        return KeyCodec.encodeIndexKey(typeId, propId, encodedValue, vertexId);
    }
}
