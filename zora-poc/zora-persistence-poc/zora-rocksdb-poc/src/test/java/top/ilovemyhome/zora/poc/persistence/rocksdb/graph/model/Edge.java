package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a directed edge in the property graph.
 * Each edge connects a source vertex to a destination vertex,
 * has a type id, and an optional map of properties.
 */
public class Edge {

    private final long srcId;
    private final long dstId;
    private final int typeId;
    private final Map<String, Object> properties;

    public Edge(long srcId, long dstId, int typeId) {
        this(srcId, dstId, typeId, new HashMap<>());
    }

    public Edge(long srcId, long dstId, int typeId, Map<String, Object> properties) {
        this.srcId = srcId;
        this.dstId = dstId;
        this.typeId = typeId;
        this.properties = new HashMap<>(properties);
    }

    public long getSrcId() {
        return srcId;
    }

    public long getDstId() {
        return dstId;
    }

    public int getTypeId() {
        return typeId;
    }

    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }

    public Edge withProperty(String key, Object value) {
        Map<String, Object> newProps = new HashMap<>(this.properties);
        newProps.put(key, value);
        return new Edge(this.srcId, this.dstId, this.typeId, newProps);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge edge)) return false;
        return srcId == edge.srcId && dstId == edge.dstId && typeId == edge.typeId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(srcId, dstId, typeId);
    }

    @Override
    public String toString() {
        return "Edge{srcId=" + srcId + ", dstId=" + dstId + ", typeId=" + typeId + ", properties=" + properties + '}';
    }
}
