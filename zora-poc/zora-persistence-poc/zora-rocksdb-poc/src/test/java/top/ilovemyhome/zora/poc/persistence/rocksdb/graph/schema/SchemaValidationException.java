package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.schema;

/**
 * Exception thrown when a vertex or edge violates the graph schema.
 */
public class SchemaValidationException extends RuntimeException {

    public SchemaValidationException(String message) {
        super(message);
    }
}
