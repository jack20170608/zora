package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Vertex;

import java.io.IOException;
import java.util.Map;

/**
 * JSON codec for graph entity values using Jackson.
 * Production environments may replace this with Protobuf or FlatBuffers for better performance.
 */
public final class ValueCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ValueCodec() {
        // utility class
    }

    // ========================== Vertex ==========================

    /**
     * Serializes a vertex to JSON bytes.
     *
     * @param vertex the vertex to serialize
     * @return JSON byte array
     */
    public static byte[] encodeVertex(Vertex vertex) {
        try {
            VertexPojo pojo = new VertexPojo(vertex.getId(), vertex.getTypeId(), vertex.getProperties());
            return MAPPER.writeValueAsBytes(pojo);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize vertex: " + vertex, e);
        }
    }

    /**
     * Deserializes JSON bytes to a vertex.
     *
     * @param bytes the JSON byte array
     * @return the deserialized vertex
     */
    public static Vertex decodeVertex(byte[] bytes) {
        try {
            VertexPojo pojo = MAPPER.readValue(bytes, VertexPojo.class);
            return new Vertex(pojo.id, pojo.typeId, pojo.properties);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize vertex", e);
        }
    }

    // ========================== Edge ==========================

    /**
     * Serializes an edge to JSON bytes.
     *
     * @param edge the edge to serialize
     * @return JSON byte array
     */
    public static byte[] encodeEdge(Edge edge) {
        try {
            EdgePojo pojo = new EdgePojo(edge.getSrcId(), edge.getDstId(), edge.getTypeId(), edge.getProperties());
            return MAPPER.writeValueAsBytes(pojo);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize edge: " + edge, e);
        }
    }

    /**
     * Deserializes JSON bytes to an edge.
     *
     * @param bytes the JSON byte array
     * @return the deserialized edge
     */
    public static Edge decodeEdge(byte[] bytes) {
        try {
            EdgePojo pojo = MAPPER.readValue(bytes, EdgePojo.class);
            return new Edge(pojo.srcId, pojo.dstId, pojo.typeId, pojo.properties);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize edge", e);
        }
    }

    // ========================== POJOs for Jackson ==========================

    /**
     * Simple POJO for vertex serialization.
     */
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

    /**
     * Simple POJO for edge serialization.
     */
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
