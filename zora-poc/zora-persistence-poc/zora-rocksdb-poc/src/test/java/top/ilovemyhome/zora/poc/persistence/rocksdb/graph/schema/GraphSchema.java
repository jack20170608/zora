package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Container for the complete graph schema definition.
 * Holds all registered vertex types and edge types.
 */
public class GraphSchema {

    private final Map<Integer, VertexTypeDef> vertexTypes = new HashMap<>();
    private final Map<Integer, EdgeTypeDef> edgeTypes = new HashMap<>();

    /**
     * Registers a vertex type definition.
     *
     * @param def the vertex type definition
     */
    public GraphSchema registerVertexType(VertexTypeDef def) {
        vertexTypes.put(def.getTypeId(), def);
        return this;
    }

    /**
     * Registers an edge type definition.
     *
     * @param def the edge type definition
     */
    public GraphSchema registerEdgeType(EdgeTypeDef def) {
        edgeTypes.put(def.getTypeId(), def);
        return this;
    }

    public VertexTypeDef getVertexType(int typeId) {
        return vertexTypes.get(typeId);
    }

    public EdgeTypeDef getEdgeType(int typeId) {
        return edgeTypes.get(typeId);
    }

    public boolean hasVertexType(int typeId) {
        return vertexTypes.containsKey(typeId);
    }

    public boolean hasEdgeType(int typeId) {
        return edgeTypes.containsKey(typeId);
    }

    public Map<Integer, VertexTypeDef> getVertexTypes() {
        return Collections.unmodifiableMap(vertexTypes);
    }

    public Map<Integer, EdgeTypeDef> getEdgeTypes() {
        return Collections.unmodifiableMap(edgeTypes);
    }
}
