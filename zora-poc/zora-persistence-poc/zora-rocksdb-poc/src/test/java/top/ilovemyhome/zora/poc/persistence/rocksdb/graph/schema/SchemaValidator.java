package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.schema;

import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Edge;
import top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model.Vertex;

import java.util.Map;

/**
 * Validates vertices and edges against a {@link GraphSchema}.
 * Throws {@link SchemaValidationException} when constraints are violated.
 */
public class SchemaValidator {

    private final GraphSchema schema;

    public SchemaValidator(GraphSchema schema) {
        this.schema = schema;
    }

    /**
     * Validates a vertex against the registered schema.
     *
     * @param vertex the vertex to validate
     * @throws SchemaValidationException if validation fails
     */
    public void validate(Vertex vertex) {
        VertexTypeDef typeDef = schema.getVertexType(vertex.getTypeId());
        if (typeDef == null) {
            throw new SchemaValidationException(
                "Unknown vertex type: " + vertex.getTypeId());
        }

        for (Map.Entry<String, Object> entry : vertex.getProperties().entrySet()) {
            String propName = entry.getKey();
            Object value = entry.getValue();

            if (!typeDef.hasProperty(propName)) {
                throw new SchemaValidationException(
                    "Vertex type '" + typeDef.getName() + "' does not allow property: " + propName);
            }

            PropertyType expectedType = typeDef.getPropertyType(propName);
            if (!matchesType(value, expectedType)) {
                throw new SchemaValidationException(
                    "Property '" + propName + "' expects " + expectedType +
                    " but got " + value.getClass().getSimpleName());
            }
        }
    }

    /**
     * Validates an edge against the registered schema.
     *
     * @param edge the edge to validate
     * @throws SchemaValidationException if validation fails
     */
    public void validate(Edge edge) {
        EdgeTypeDef typeDef = schema.getEdgeType(edge.getTypeId());
        if (typeDef == null) {
            throw new SchemaValidationException(
                "Unknown edge type: " + edge.getTypeId());
        }

        for (Map.Entry<String, Object> entry : edge.getProperties().entrySet()) {
            String propName = entry.getKey();
            Object value = entry.getValue();

            if (!typeDef.hasProperty(propName)) {
                throw new SchemaValidationException(
                    "Edge type '" + typeDef.getName() + "' does not allow property: " + propName);
            }

            PropertyType expectedType = typeDef.getPropertyType(propName);
            if (!matchesType(value, expectedType)) {
                throw new SchemaValidationException(
                    "Property '" + propName + "' expects " + expectedType +
                    " but got " + value.getClass().getSimpleName());
            }
        }
    }

    /**
     * Validates that an edge connects vertices of the expected types.
     * Note: this requires looking up the actual vertex types from storage.
     *
     * @param edge           the edge to validate
     * @param srcVertexType  the source vertex type id
     * @param dstVertexType  the destination vertex type id
     * @throws SchemaValidationException if endpoint types don't match
     */
    public void validateEndpoints(Edge edge, int srcVertexType, int dstVertexType) {
        EdgeTypeDef typeDef = schema.getEdgeType(edge.getTypeId());
        if (typeDef == null) {
            throw new SchemaValidationException(
                "Unknown edge type: " + edge.getTypeId());
        }

        if (typeDef.getSrcTypeId() != srcVertexType) {
            throw new SchemaValidationException(
                "Edge '" + typeDef.getName() + "' requires source vertex type " +
                typeDef.getSrcTypeId() + " but got " + srcVertexType);
        }

        if (typeDef.getDstTypeId() != dstVertexType) {
            throw new SchemaValidationException(
                "Edge '" + typeDef.getName() + "' requires destination vertex type " +
                typeDef.getDstTypeId() + " but got " + dstVertexType);
        }
    }

    private boolean matchesType(Object value, PropertyType expected) {
        return switch (expected) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer;
            case LONG -> value instanceof Long || value instanceof Integer;
            case DOUBLE -> value instanceof Double || value instanceof Float;
            case BOOLEAN -> value instanceof Boolean;
        };
    }
}
