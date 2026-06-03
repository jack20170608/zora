package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines the schema for an edge type, including endpoint type constraints and properties.
 */
public class EdgeTypeDef {

    private final int typeId;
    private final String name;
    private final int srcTypeId;
    private final int dstTypeId;
    private final Map<String, PropertyType> properties;

    public EdgeTypeDef(int typeId, String name, int srcTypeId, int dstTypeId) {
        this(typeId, name, srcTypeId, dstTypeId, new HashMap<>());
    }

    public EdgeTypeDef(int typeId, String name, int srcTypeId, int dstTypeId,
                        Map<String, PropertyType> properties) {
        this.typeId = typeId;
        this.name = name;
        this.srcTypeId = srcTypeId;
        this.dstTypeId = dstTypeId;
        this.properties = new HashMap<>(properties);
    }

    public EdgeTypeDef withProperty(String name, PropertyType type) {
        Map<String, PropertyType> newProps = new HashMap<>(this.properties);
        newProps.put(name, type);
        return new EdgeTypeDef(this.typeId, this.name, this.srcTypeId, this.dstTypeId, newProps);
    }

    public int getTypeId() {
        return typeId;
    }

    public String getName() {
        return name;
    }

    public int getSrcTypeId() {
        return srcTypeId;
    }

    public int getDstTypeId() {
        return dstTypeId;
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
