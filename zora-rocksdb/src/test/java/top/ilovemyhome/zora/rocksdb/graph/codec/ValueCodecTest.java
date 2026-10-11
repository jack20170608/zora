package top.ilovemyhome.zora.rocksdb.graph.codec;

import org.junit.jupiter.api.Test;
import top.ilovemyhome.zora.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.rocksdb.graph.model.Vertex;

import static org.assertj.core.api.Assertions.assertThat;

class ValueCodecTest {

    @Test
    void roundTripsVerticesAsSmile() {
        Vertex vertex = new Vertex(42L, 3).withProperty("name", "Alice").withProperty("age", 30);

        byte[] encoded = ValueCodec.encodeVertex(vertex);

        assertThat(encoded).startsWith((byte) 0x3A, (byte) 0x29, (byte) 0x0A);
        Vertex decoded = ValueCodec.decodeVertex(encoded);
        assertThat(decoded.getId()).isEqualTo(vertex.getId());
        assertThat(decoded.getTypeId()).isEqualTo(vertex.getTypeId());
        assertThat(decoded.getProperties()).isEqualTo(vertex.getProperties());
    }

    @Test
    void roundTripsEdgesAsSmile() {
        Edge edge = new Edge(1L, 2L, 4).withProperty("since", 2024);

        Edge decoded = ValueCodec.decodeEdge(ValueCodec.encodeEdge(edge));

        assertThat(decoded.getSrcId()).isEqualTo(edge.getSrcId());
        assertThat(decoded.getDstId()).isEqualTo(edge.getDstId());
        assertThat(decoded.getTypeId()).isEqualTo(edge.getTypeId());
        assertThat(decoded.getProperties()).isEqualTo(edge.getProperties());
    }
}
