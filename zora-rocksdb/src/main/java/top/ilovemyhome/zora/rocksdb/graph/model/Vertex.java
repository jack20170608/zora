package top.ilovemyhome.zora.rocksdb.graph.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a vertex (node) in the property graph.
 * Each vertex has a unique id, a type id, and an optional map of properties.
 */
public class Vertex {

    private final long id;
    private final int typeId;
    private final Map<String, Object> properties;

    public Vertex(long id, int typeId) {
        this(id, typeId, new HashMap<>());
    }

    public Vertex(long id, int typeId, Map<String, Object> properties) {
        this.id = id;
        this.typeId = typeId;
        this.properties = new HashMap<>(properties);
    }

    public long getId() {
        return id;
    }

    public int getTypeId() {
        return typeId;
    }

    public Map<String, Object> getProperties() {
        return new HashMap<>(properties);
    }

    public Vertex withProperty(String key, Object value) {
        Map<String, Object> newProps = new HashMap<>(this.properties);
        newProps.put(key, value);
        return new Vertex(this.id, this.typeId, newProps);
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vertex vertex)) return false;
        return id == vertex.id && typeId == vertex.typeId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, typeId);
    }

    @Override
    public String toString() {
        return "Vertex{id=" + id + ", typeId=" + typeId + ", properties=" + properties + '}';
    }
}
