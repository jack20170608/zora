package top.ilovemyhome.zora.rocksdb.graph.codec;

import java.nio.ByteBuffer;

/**
 * Binary codec for graph entity keys.
 * All numeric fields use big-endian so that the RocksDB lexicographical key
 * order matches the natural numeric order, which lets every traversal and
 * range query reduce to a {@code seek + prefix-scan}.
 *
 * <h2>Key formats</h2>
 * <pre>
 *   Vertex:  [ V(1) | typeId(4) | vertexId(8) ]                              13 bytes
 *   OutEdge: [ E(1) | srcId(8)  | edgeType(4) | 'O' | dstId(8) ]             22 bytes
 *   InEdge:  [ E(1) | dstId(8)  | edgeType(4) | 'I' | srcId(8) ]             22 bytes
 *   Index:   [ I(1) | typeId(4) | propId(4)   | encodedValue | vertexId(8) ] variable
 * </pre>
 *
 * <p>The index key layout puts {@code typeId} in the header so callers
 * materializing a hit never need the slow {@code getVertex(long)} fallback.
 *
 * <p>{@code encodedValue} is produced by
 * {@link top.ilovemyhome.zora.rocksdb.graph.model.Property#encodeValue(Object)}
 * and consists of a 1-byte type tag followed by an order-preserving payload,
 * which is what makes range queries on the property index possible.
 */
public final class KeyCodec {

    private static final byte PREFIX_VERTEX = 'V';
    private static final byte PREFIX_EDGE   = 'E';
    private static final byte PREFIX_INDEX  = 'I';
    /** Edge property index prefix - 'J' to stay adjacent to 'I' in ASCII for easy visual grep. */
    private static final byte PREFIX_INDEX_EDGE = 'J';

    private static final byte DIR_OUT = 'O';
    private static final byte DIR_IN  = 'I';

    private static final int VERTEX_KEY_LEN = 1 + 4 + 8;       // 13
    private static final int EDGE_KEY_LEN   = 1 + 8 + 4 + 1 + 8; // 22

    /** Length of the fixed header in an index key: prefix + typeId + propId. */
    public static final int INDEX_HEADER_LEN = 1 + 4 + 4;
    /** Length of the trailing vertexId in an index key. */
    public static final int INDEX_TRAILER_LEN = 8;

    private KeyCodec() {
        // utility class
    }

    // ========================== Vertex Keys ==========================

    /**
     * Encodes a vertex key: {@code V | typeId | vertexId}.
     */
    public static byte[] encodeVertexKey(int typeId, long vertexId) {
        ByteBuffer buf = ByteBuffer.allocate(VERTEX_KEY_LEN);
        buf.put(PREFIX_VERTEX);
        buf.putInt(typeId);
        buf.putLong(vertexId);
        return buf.array();
    }

    public static long decodeVertexId(byte[] key) {
        return ByteBuffer.wrap(key).getLong(1 + 4);
    }

    public static int decodeVertexTypeId(byte[] key) {
        return ByteBuffer.wrap(key).getInt(1);
    }

    /**
     * Returns a 5-byte prefix that selects every vertex of a given type.
     */
    public static byte[] vertexPrefix(int typeId) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 4);
        buf.put(PREFIX_VERTEX);
        buf.putInt(typeId);
        return buf.array();
    }

    // ========================== Edge Keys ==========================

    /**
     * Encodes a bidirectional edge entry. The same logical edge is stored
     * twice (OUT on the source, IN on the destination), so traversal from
     * either endpoint is a pure prefix scan.
     */
    public static byte[] encodeEdgeKey(long firstId, int edgeType, Direction dir, long secondId) {
        ByteBuffer buf = ByteBuffer.allocate(EDGE_KEY_LEN);
        buf.put(PREFIX_EDGE);
        buf.putLong(firstId);
        buf.putInt(edgeType);
        buf.put(dir.code);
        buf.putLong(secondId);
        return buf.array();
    }

    public static byte[] encodeOutEdgeKey(long srcId, int edgeType, long dstId) {
        return encodeEdgeKey(srcId, edgeType, Direction.OUT, dstId);
    }

    public static byte[] encodeInEdgeKey(long dstId, int edgeType, long srcId) {
        return encodeEdgeKey(dstId, edgeType, Direction.IN, srcId);
    }

    /**
     * Returns a 14-byte prefix for all edges of (vertexId, edgeType, direction).
     */
    public static byte[] edgePrefix(long vertexId, int edgeType, Direction dir) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 8 + 4 + 1);
        buf.put(PREFIX_EDGE);
        buf.putLong(vertexId);
        buf.putInt(edgeType);
        buf.put(dir.code);
        return buf.array();
    }

    /**
     * Returns a 9-byte prefix that selects every edge attached to a vertex,
     * regardless of direction or type. Used by {@code removeVertex} to wipe
     * every incident edge in a single iterator pass.
     */
    public static byte[] edgePrefix(long vertexId) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 8);
        buf.put(PREFIX_EDGE);
        buf.putLong(vertexId);
        return buf.array();
    }

    public static long decodeEdgeSrcId(byte[] key) {
        ByteBuffer buf = ByteBuffer.wrap(key);
        byte dir = buf.get(1 + 8 + 4);
        return dir == DIR_OUT ? buf.getLong(1) : buf.getLong(1 + 8 + 4 + 1);
    }

    public static long decodeEdgeDstId(byte[] key) {
        ByteBuffer buf = ByteBuffer.wrap(key);
        byte dir = buf.get(1 + 8 + 4);
        return dir == DIR_OUT ? buf.getLong(1 + 8 + 4 + 1) : buf.getLong(1);
    }

    public static int decodeEdgeType(byte[] key) {
        return ByteBuffer.wrap(key).getInt(1 + 8);
    }

    // ========================== Index Keys ==========================

    /**
     * Encodes a full index entry key.
     *
     * @param typeId       the vertex type id
     * @param propId       the per-database property id (assigned by the schema
     *                     dictionary); 4-byte numeric id replaces the old hash
     *                     to eliminate collisions
     * @param encodedValue tag + order-preserving payload from
     *                     {@link top.ilovemyhome.zora.rocksdb.graph.model.Property#encodeValue(Object)}
     * @param vertexId     the owning vertex id (trailing position so multiple
     *                     vertices can share the same value)
     */
    public static byte[] encodeIndexKey(int typeId, int propId, byte[] encodedValue, long vertexId) {
        ByteBuffer buf = ByteBuffer.allocate(INDEX_HEADER_LEN + encodedValue.length + INDEX_TRAILER_LEN);
        buf.put(PREFIX_INDEX);
        buf.putInt(typeId);
        buf.putInt(propId);
        buf.put(encodedValue);
        buf.putLong(vertexId);
        return buf.array();
    }

    /**
     * Returns the 9-byte prefix selecting every index entry for a given
     * (typeId, propId) pair, regardless of value.
     */
    public static byte[] indexPrefix(int typeId, int propId) {
        ByteBuffer buf = ByteBuffer.allocate(INDEX_HEADER_LEN);
        buf.put(PREFIX_INDEX);
        buf.putInt(typeId);
        buf.putInt(propId);
        return buf.array();
    }

    /**
     * Returns the prefix selecting every index entry for one exact value.
     */
    public static byte[] indexValuePrefix(int typeId, int propId, byte[] encodedValue) {
        ByteBuffer buf = ByteBuffer.allocate(INDEX_HEADER_LEN + encodedValue.length);
        buf.put(PREFIX_INDEX);
        buf.putInt(typeId);
        buf.putInt(propId);
        buf.put(encodedValue);
        return buf.array();
    }

    /**
     * Returns the smallest possible index key for a range scan lower bound.
     */
    public static byte[] indexRangeLowerBound(int typeId, int propId, byte[] lowEncodedValue) {
        ByteBuffer buf = ByteBuffer.allocate(INDEX_HEADER_LEN + lowEncodedValue.length + INDEX_TRAILER_LEN);
        buf.put(PREFIX_INDEX);
        buf.putInt(typeId);
        buf.putInt(propId);
        buf.put(lowEncodedValue);
        return buf.array();
    }

    /**
     * Returns the largest possible index key for a range scan upper bound.
     */
    public static byte[] indexRangeUpperBound(int typeId, int propId, byte[] highEncodedValue) {
        ByteBuffer buf = ByteBuffer.allocate(INDEX_HEADER_LEN + highEncodedValue.length + INDEX_TRAILER_LEN);
        buf.put(PREFIX_INDEX);
        buf.putInt(typeId);
        buf.putInt(propId);
        buf.put(highEncodedValue);
        for (int i = buf.position(); i < buf.capacity(); i++) {
            buf.put((byte) 0xFF);
        }
        return buf.array();
    }

    public static int decodeIndexTypeId(byte[] indexKey) {
        return ByteBuffer.wrap(indexKey).getInt(1);
    }

    public static long decodeIndexVertexId(byte[] indexKey) {
        return ByteBuffer.wrap(indexKey).getLong(indexKey.length - INDEX_TRAILER_LEN);
    }

    // ========================== Edge Index Keys ==========================
    //
    // Three flavours of edge index keys live side-by-side in cf_edge_index,
    // discriminated by the second byte (P / S / D). One logical edge attribute
    // gets written 3 times - the write amplification buys us O(1) prefix
    // scans for every meaningful query shape:
    //
    //   J | P | eTypeId(4) | propId(4) | encVal | srcId(8) | dstId(8)
    //     - global "every edge of type T whose prop = v"
    //
    //   J | S | srcId(8)   | eTypeId(4) | propId(4) | encVal | dstId(8)
    //     - "every OUT edge of src with prop = v" (no extra filter needed)
    //
    //   J | D | dstId(8)   | eTypeId(4) | propId(4) | encVal | srcId(8)
    //     - symmetric IN-edge variant
    //
    // The trailing endpoint(s) are inside the key so the same (eType, prop,
    // value) shared by multiple edges does not collide.

    private static final byte EI_FLAVOR_GLOBAL = 'P';
    private static final byte EI_FLAVOR_SRC    = 'S';
    private static final byte EI_FLAVOR_DST    = 'D';

    // Global flavour:  J | P | eTypeId(4) | propId(4) | encVal | srcId(8) | dstId(8)
    /** Header length of a global edge index key, up to and including propId. */
    public static final int EI_GLOBAL_HEADER_LEN  = 2 + 4 + 4;
    /** Trailer length of a global edge index key: srcId + dstId. */
    public static final int EI_GLOBAL_TRAILER_LEN = 8 + 8;

    // Endpoint flavours (SRC / DST): J | F | endpointId(8) | eTypeId(4) | propId(4) | encVal | otherEndpointId(8)
    /** Header length of an endpoint-keyed edge index key, up to propId. */
    public static final int EI_ENDPOINT_HEADER_LEN  = 2 + 8 + 4 + 4;
    /** Trailer length of an endpoint-keyed edge index key: the other endpoint. */
    public static final int EI_ENDPOINT_TRAILER_LEN = 8;

    // -------------------- Global edge index --------------------

    /**
     * Encodes a global edge index entry {@code J | P | eType | propId | encVal | src | dst}.
     */
    public static byte[] encodeEdgeIndexGlobalKey(int edgeType, int propId, byte[] encodedValue,
                                                  long srcId, long dstId) {
        ByteBuffer buf = ByteBuffer.allocate(
            EI_GLOBAL_HEADER_LEN + encodedValue.length + EI_GLOBAL_TRAILER_LEN);
        buf.put(PREFIX_INDEX_EDGE);
        buf.put(EI_FLAVOR_GLOBAL);
        buf.putInt(edgeType);
        buf.putInt(propId);
        buf.put(encodedValue);
        buf.putLong(srcId);
        buf.putLong(dstId);
        return buf.array();
    }

    /**
     * Returns the prefix selecting every global edge index entry for one
     * exact {@code (eType, propId, value)}.
     */
    public static byte[] edgeIndexGlobalValuePrefix(int edgeType, int propId, byte[] encodedValue) {
        ByteBuffer buf = ByteBuffer.allocate(EI_GLOBAL_HEADER_LEN + encodedValue.length);
        buf.put(PREFIX_INDEX_EDGE);
        buf.put(EI_FLAVOR_GLOBAL);
        buf.putInt(edgeType);
        buf.putInt(propId);
        buf.put(encodedValue);
        return buf.array();
    }

    /**
     * Returns the lower bound for a global-edge range scan: the trailing
     * endpoint pair is zero-filled.
     */
    public static byte[] edgeIndexGlobalRangeLowerBound(int edgeType, int propId,
                                                        byte[] lowEncodedValue) {
        ByteBuffer buf = ByteBuffer.allocate(
            EI_GLOBAL_HEADER_LEN + lowEncodedValue.length + EI_GLOBAL_TRAILER_LEN);
        buf.put(PREFIX_INDEX_EDGE);
        buf.put(EI_FLAVOR_GLOBAL);
        buf.putInt(edgeType);
        buf.putInt(propId);
        buf.put(lowEncodedValue);
        return buf.array();
    }

    /**
     * Returns the upper bound for a global-edge range scan: the trailing
     * endpoint pair is set to 0xFF so the inclusive max is captured.
     */
    public static byte[] edgeIndexGlobalRangeUpperBound(int edgeType, int propId,
                                                        byte[] highEncodedValue) {
        ByteBuffer buf = ByteBuffer.allocate(
            EI_GLOBAL_HEADER_LEN + highEncodedValue.length + EI_GLOBAL_TRAILER_LEN);
        buf.put(PREFIX_INDEX_EDGE);
        buf.put(EI_FLAVOR_GLOBAL);
        buf.putInt(edgeType);
        buf.putInt(propId);
        buf.put(highEncodedValue);
        for (int i = buf.position(); i < buf.capacity(); i++) {
            buf.put((byte) 0xFF);
        }
        return buf.array();
    }

    /** Pulls {@code (eType, srcId, dstId)} out of a global edge index key. */
    public static int  decodeEdgeIndexGlobalType(byte[] key)  { return ByteBuffer.wrap(key).getInt(2); }
    public static long decodeEdgeIndexGlobalSrc (byte[] key)  {
        return ByteBuffer.wrap(key).getLong(key.length - EI_GLOBAL_TRAILER_LEN);
    }
    public static long decodeEdgeIndexGlobalDst (byte[] key)  {
        return ByteBuffer.wrap(key).getLong(key.length - 8);
    }

    // -------------------- Endpoint-keyed edge index --------------------

    /**
     * Encodes an endpoint-keyed edge index entry:
     * {@code J | flavor | endpointId | eType | propId | encVal | otherEndpointId}.
     *
     * @param srcSide true to write the SRC-flavored key (used for OUT-edge
     *                queries from a vertex), false to write the DST-flavored
     *                key (used for IN-edge queries).
     */
    public static byte[] encodeEdgeIndexEndpointKey(boolean srcSide, long endpointId,
                                                    int edgeType, int propId,
                                                    byte[] encodedValue, long otherEndpointId) {
        ByteBuffer buf = ByteBuffer.allocate(
            EI_ENDPOINT_HEADER_LEN + encodedValue.length + EI_ENDPOINT_TRAILER_LEN);
        buf.put(PREFIX_INDEX_EDGE);
        buf.put(srcSide ? EI_FLAVOR_SRC : EI_FLAVOR_DST);
        buf.putLong(endpointId);
        buf.putInt(edgeType);
        buf.putInt(propId);
        buf.put(encodedValue);
        buf.putLong(otherEndpointId);
        return buf.array();
    }

    /**
     * Returns the prefix selecting every endpoint-keyed edge index entry for
     * an exact {@code (endpoint, eType, propId, value)}.
     */
    public static byte[] edgeIndexEndpointValuePrefix(boolean srcSide, long endpointId,
                                                      int edgeType, int propId,
                                                      byte[] encodedValue) {
        ByteBuffer buf = ByteBuffer.allocate(EI_ENDPOINT_HEADER_LEN + encodedValue.length);
        buf.put(PREFIX_INDEX_EDGE);
        buf.put(srcSide ? EI_FLAVOR_SRC : EI_FLAVOR_DST);
        buf.putLong(endpointId);
        buf.putInt(edgeType);
        buf.putInt(propId);
        buf.put(encodedValue);
        return buf.array();
    }

    public static byte[] edgeIndexEndpointRangeLowerBound(boolean srcSide, long endpointId,
                                                          int edgeType, int propId,
                                                          byte[] lowEncodedValue) {
        ByteBuffer buf = ByteBuffer.allocate(
            EI_ENDPOINT_HEADER_LEN + lowEncodedValue.length + EI_ENDPOINT_TRAILER_LEN);
        buf.put(PREFIX_INDEX_EDGE);
        buf.put(srcSide ? EI_FLAVOR_SRC : EI_FLAVOR_DST);
        buf.putLong(endpointId);
        buf.putInt(edgeType);
        buf.putInt(propId);
        buf.put(lowEncodedValue);
        return buf.array();
    }

    public static byte[] edgeIndexEndpointRangeUpperBound(boolean srcSide, long endpointId,
                                                          int edgeType, int propId,
                                                          byte[] highEncodedValue) {
        ByteBuffer buf = ByteBuffer.allocate(
            EI_ENDPOINT_HEADER_LEN + highEncodedValue.length + EI_ENDPOINT_TRAILER_LEN);
        buf.put(PREFIX_INDEX_EDGE);
        buf.put(srcSide ? EI_FLAVOR_SRC : EI_FLAVOR_DST);
        buf.putLong(endpointId);
        buf.putInt(edgeType);
        buf.putInt(propId);
        buf.put(highEncodedValue);
        for (int i = buf.position(); i < buf.capacity(); i++) {
            buf.put((byte) 0xFF);
        }
        return buf.array();
    }

    public static long decodeEdgeIndexEndpointOther(byte[] key) {
        return ByteBuffer.wrap(key).getLong(key.length - EI_ENDPOINT_TRAILER_LEN);
    }

    public static int decodeEdgeIndexEndpointType(byte[] key) {
        return ByteBuffer.wrap(key).getInt(2 + 8);
    }

    // ========================== Utility ==========================

    /**
     * Returns true iff {@code key} starts with the given {@code prefix}.
     */
    public static boolean startsWith(byte[] key, byte[] prefix) {
        if (key.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (key[i] != prefix[i]) return false;
        }
        return true;
    }

    /**
     * Unsigned lexicographical comparison between two byte arrays, used by
     * the range-scan loop to test against the upper bound.
     */
    public static int compareUnsigned(byte[] a, byte[] b) {
        int min = Math.min(a.length, b.length);
        for (int i = 0; i < min; i++) {
            int diff = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (diff != 0) return diff;
        }
        return a.length - b.length;
    }

    /**
     * Direction of an edge entry.
     */
    public enum Direction {
        OUT(DIR_OUT),
        IN(DIR_IN);

        private final byte code;

        Direction(byte code) {
            this.code = code;
        }

        public byte getCode() {
            return code;
        }
    }
}