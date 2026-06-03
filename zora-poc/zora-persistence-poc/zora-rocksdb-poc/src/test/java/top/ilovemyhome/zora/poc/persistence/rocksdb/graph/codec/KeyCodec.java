package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.codec;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Binary codec for graph entity keys.
 * All keys use big-endian encoding to ensure lexicographical order matches numeric order.
 *
 * Key formats:
 * - Vertex:     [V(1) | typeId(4) | vertexId(8)]              = 13 bytes
 * - Out Edge:   [E(1) | srcId(8) | edgeType(4) | 'O'(1) | dstId(8)]   = 22 bytes
 * - In Edge:    [E(1) | dstId(8) | edgeType(4) | 'I'(1) | srcId(8)]   = 22 bytes
 * - Index:      [I(1) | typeId(4) | propHash(4) | value... | vertexId(8)] = variable
 */
public final class KeyCodec {

    private static final byte PREFIX_VERTEX = 'V';
    private static final byte PREFIX_EDGE = 'E';
    private static final byte PREFIX_INDEX = 'I';

    private static final byte DIR_OUT = 'O';
    private static final byte DIR_IN = 'I';

    private static final int VERTEX_KEY_LEN = 1 + 4 + 8;       // 13
    private static final int EDGE_KEY_LEN = 1 + 8 + 4 + 1 + 8; // 22

    private KeyCodec() {
        // utility class
    }

    // ========================== Vertex Keys ==========================

    /**
     * Encodes a vertex key.
     *
     * @param typeId   the vertex type id
     * @param vertexId the vertex unique id
     * @return 13-byte key
     */
    public static byte[] encodeVertexKey(int typeId, long vertexId) {
        ByteBuffer buf = ByteBuffer.allocate(VERTEX_KEY_LEN);
        buf.put(PREFIX_VERTEX);
        buf.putInt(typeId);
        buf.putLong(vertexId);
        return buf.array();
    }

    /**
     * Decodes a vertex key to extract the vertex id.
     *
     * @param key the vertex key bytes
     * @return the vertex id
     */
    public static long decodeVertexId(byte[] key) {
        return ByteBuffer.wrap(key).getLong(1 + 4);
    }

    /**
     * Decodes a vertex key to extract the type id.
     *
     * @param key the vertex key bytes
     * @return the type id
     */
    public static int decodeVertexTypeId(byte[] key) {
        return ByteBuffer.wrap(key).getInt(1);
    }

    /**
     * Returns the prefix for scanning all vertices of a given type.
     *
     * @param typeId the vertex type id
     * @return 5-byte prefix
     */
    public static byte[] vertexPrefix(int typeId) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 4);
        buf.put(PREFIX_VERTEX);
        buf.putInt(typeId);
        return buf.array();
    }

    // ========================== Edge Keys ==========================

    /**
     * Encodes an edge key.
     *
     * @param firstId  the primary id (src for out, dst for in)
     * @param edgeType the edge type id
     * @param dir      the direction ('O' or 'I')
     * @param secondId the secondary id (dst for out, src for in)
     * @return 22-byte key
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

    /**
     * Encodes an outgoing edge key: E | srcId | edgeType | 'O' | dstId.
     */
    public static byte[] encodeOutEdgeKey(long srcId, int edgeType, long dstId) {
        return encodeEdgeKey(srcId, edgeType, Direction.OUT, dstId);
    }

    /**
     * Encodes an incoming edge key: E | dstId | edgeType | 'I' | srcId.
     */
    public static byte[] encodeInEdgeKey(long dstId, int edgeType, long srcId) {
        return encodeEdgeKey(dstId, edgeType, Direction.IN, srcId);
    }

    /**
     * Returns the prefix for scanning edges of a vertex in a given direction.
     *
     * @param vertexId the vertex id (src for out, dst for in)
     * @param edgeType the edge type id
     * @param dir      the direction
     * @return 14-byte prefix
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
     * Decodes the source id from an edge key.
     * For outgoing edges, src is at offset 1; for incoming, src is at offset 14.
     */
    public static long decodeEdgeSrcId(byte[] key) {
        ByteBuffer buf = ByteBuffer.wrap(key);
        byte dir = buf.get(1 + 8 + 4);
        if (dir == DIR_OUT) {
            return buf.getLong(1);
        } else {
            return buf.getLong(1 + 8 + 4 + 1);
        }
    }

    /**
     * Decodes the destination id from an edge key.
     */
    public static long decodeEdgeDstId(byte[] key) {
        ByteBuffer buf = ByteBuffer.wrap(key);
        byte dir = buf.get(1 + 8 + 4);
        if (dir == DIR_OUT) {
            return buf.getLong(1 + 8 + 4 + 1);
        } else {
            return buf.getLong(1);
        }
    }

    /**
     * Decodes the edge type from an edge key.
     */
    public static int decodeEdgeType(byte[] key) {
        return ByteBuffer.wrap(key).getInt(1 + 8);
    }

    // ========================== Index Keys ==========================

    /**
     * Encodes an index key for property lookup.
     * Format: I | typeId(4) | propHash(4) | valueBytes | vertexId(8)
     *
     * @param typeId   the vertex type id
     * @param propHash the hash of the property name
     * @param value    the encoded property value bytes
     * @param vertexId the vertex id
     * @return variable-length index key
     */
    public static byte[] encodeIndexKey(int typeId, int propHash, byte[] value, long vertexId) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 4 + value.length + 8);
        buf.put(PREFIX_INDEX);
        buf.putInt(typeId);
        buf.putInt(propHash);
        buf.put(value);
        buf.putLong(vertexId);
        return buf.array();
    }

    /**
     * Returns the prefix for scanning index entries of a specific property.
     *
     * @param typeId   the vertex type id
     * @param propHash the property name hash
     * @return 9-byte prefix
     */
    public static byte[] indexPrefix(int typeId, int propHash) {
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 4);
        buf.put(PREFIX_INDEX);
        buf.putInt(typeId);
        buf.putInt(propHash);
        return buf.array();
    }

    // ========================== Utility ==========================

    /**
     * Checks if the given key starts with the specified prefix.
     */
    public static boolean startsWith(byte[] key, byte[] prefix) {
        if (key.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (key[i] != prefix[i]) return false;
        }
        return true;
    }

    /**
     * Direction of an edge.
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
