package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines the schema for a vertex type, including allowed properties and their types.
 */
public class VertexTypeDef {

    private final int typeId;
    private final String name;
    private final Map<String, PropertyType> properties;

    public VertexTypeDef(int typeId, String name) {
        this(typeId, name, new HashMap<>());
    }

    public VertexTypeDef(int typeId, String name, Map<String, PropertyType> properties) {
        this.typeId = typeId;
        this.name = name;
        this.properties = new HashMap<>(properties);
    }

    public VertexTypeDef withProperty(String name, PropertyType type) {
        Map<String, PropertyType> newProps = new HashMap<>(this.properties);
        newProps.put(name, type);
        return new VertexTypeDef(this.typeId, this.name, newProps);
    }

    public int getTypeId() {
        return typeId;
    }

    public String getName() {
        return name;
    }

    public Map<String, PropertyType> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public PropertyType getPropertyType(String name) {
        return properties.get(name);
    }

    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }
}
