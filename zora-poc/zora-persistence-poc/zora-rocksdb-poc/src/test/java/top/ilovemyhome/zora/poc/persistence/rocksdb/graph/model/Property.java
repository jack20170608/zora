package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for property serialization and conversion.
 * Provides methods to encode property values into bytes for indexing purposes.
 */
public final class Property {

    private Property() {
        // utility class
    }

    /**
     * Encodes a property value into a byte array suitable for index keys.
     * Supports String, Integer, Long, Double, and Boolean types.
     *
     * @param value the property value
     * @return byte array representation
     * @throws IllegalArgumentException if the type is not supported
     */
    public static byte[] encodeValue(Object value) {
        if (value == null) {
            return new byte[0];
        }
        if (value instanceof String s) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            // Prefix with length for proper lexicographical ordering
            byte[] result = new byte[4 + bytes.length];
            result[0] = (byte) ((bytes.length >> 24) & 0xFF);
            result[1] = (byte) ((bytes.length >> 16) & 0xFF);
            result[2] = (byte) ((bytes.length >> 8) & 0xFF);
            result[3] = (byte) (bytes.length & 0xFF);
            System.arraycopy(bytes, 0, result, 4, bytes.length);
            return result;
        }
        if (value instanceof Integer i) {
            return intToBytes(i);
        }
        if (value instanceof Long l) {
            return longToBytes(l);
        }
        if (value instanceof Double d) {
            return longToBytes(Double.doubleToRawLongBits(d));
        }
        if (value instanceof Boolean b) {
            return new byte[]{b ? (byte) 1 : (byte) 0};
        }
        throw new IllegalArgumentException("Unsupported property type: " + value.getClass());
    }

    /**
     * Computes a simple hash for a property name.
     * Used as a component in index keys.
     *
     * @param name the property name
     * @return 4-byte hash value
     */
    public static int hashName(String name) {
        return name.hashCode();
    }

    /**
     * Converts a map of properties into a serializable format.
     * All values are kept as-is (assumed JSON-serializable).
     *
     * @param properties the property map
     * @return a new map safe for serialization
     */
    public static Map<String, Object> normalize(Map<String, Object> properties) {
        return new HashMap<>(properties);
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
            (byte) ((value >> 24) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }

    private static byte[] longToBytes(long value) {
        return new byte[]{
            (byte) ((value >> 56) & 0xFF),
            (byte) ((value >> 48) & 0xFF),
            (byte) ((value >> 40) & 0xFF),
            (byte) ((value >> 32) & 0xFF),
            (byte) ((value >> 24) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }
}
