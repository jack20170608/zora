package top.ilovemyhome.zora.rocksdb.graph.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import top.ilovemyhome.zora.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;

import java.io.IOException;
import java.util.Map;

/**
 * Binary codec for graph entity values using Jackson Smile.
 *
 * <p>Smile is a binary superset of JSON (same data model, same Jackson
 * databind layer, same POJOs) but encoded as a compact tag-prefixed binary
 * stream. Compared to plain JSON it cuts both encode and decode time
 * roughly in half, with no API-level change for callers - the bytes
 * written into cf_vertex / cf_edge are simply binary instead of UTF-8
 * JSON text.
 *
 * <p><b>Backwards compatibility:</b> Smile-encoded blobs start with a
 * 4-byte magic header {@code 3A 29 0A ...}; plain JSON starts with
 * {@code '{'} (0x7B). They are not mutually readable. Existing JSON-format
 * databases must be re-imported or this class must be reverted before
 * opening them.
 */
public final class ValueCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper(new SmileFactory());

    private ValueCodec() {
        // utility class
    }

    // ========================== Vertex ==========================

    public static byte[] encodeVertex(Vertex vertex) {
        try {
            VertexPojo pojo = new VertexPojo(vertex.getId(), vertex.getTypeId(), vertex.getProperties());
            return MAPPER.writeValueAsBytes(pojo);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize vertex: " + vertex, e);
        }
    }

    public static Vertex decodeVertex(byte[] bytes) {
        try {
            VertexPojo pojo = MAPPER.readValue(bytes, VertexPojo.class);
            return new Vertex(pojo.id, pojo.typeId, pojo.properties);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize vertex", e);
        }
    }

    // ========================== Edge ==========================

    public static byte[] encodeEdge(Edge edge) {
        try {
            EdgePojo pojo = new EdgePojo(edge.getSrcId(), edge.getDstId(), edge.getTypeId(), edge.getProperties());
            return MAPPER.writeValueAsBytes(pojo);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize edge: " + edge, e);
        }
    }

    public static Edge decodeEdge(byte[] bytes) {
        try {
            EdgePojo pojo = MAPPER.readValue(bytes, EdgePojo.class);
            return new Edge(pojo.srcId, pojo.dstId, pojo.typeId, pojo.properties);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize edge", e);
        }
    }

    // ========================== POJOs for Jackson ==========================

    public static class VertexPojo {
        public long id;
        public int typeId;
        public Map<String, Object> properties;

        public VertexPojo() {
        }

        public VertexPojo(long id, int typeId, Map<String, Object> properties) {
            this.id = id;
            this.typeId = typeId;
            this.properties = properties;
        }
    }

    public static class EdgePojo {
        public long srcId;
        public long dstId;
        public int typeId;
        public Map<String, Object> properties;

        public EdgePojo() {
        }

        public EdgePojo(long srcId, long dstId, int typeId, Map<String, Object> properties) {
            this.srcId = srcId;
            this.dstId = dstId;
            this.typeId = typeId;
            this.properties = properties;
        }
    }
}
