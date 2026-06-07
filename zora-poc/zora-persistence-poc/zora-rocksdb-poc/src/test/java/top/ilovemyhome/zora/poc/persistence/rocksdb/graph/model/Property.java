package top.ilovemyhome.zora.poc.persistence.rocksdb.graph.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for property value serialization.
 *
 * <p>The encoded byte layout used in secondary-index keys is:
 * <pre>
 *   [ tag(1) | fixedValue(variable) ]
 * </pre>
 * The {@code tag} byte allows the same property name to host values of
 * different physical types without ambiguity, and the {@code fixedValue} is
 * an order-preserving encoding so that the RocksDB lexicographical key order
 * matches the natural value order. This is the foundation for range queries
 * against the property index.
 *
 * <p>Type-specific encodings:
 * <ul>
 *   <li>{@code long / int} - big-endian with sign bit flipped, 8 bytes.</li>
 *   <li>{@code double}     - IEEE 754 bits with a custom flip so that
 *       negative doubles sort before positive ones, 8 bytes.</li>
 *   <li>{@code boolean}    - 1 byte, {@code 0x00 / 0x01}.</li>
 *   <li>{@code string}     - UTF-8 with {@code 0x00} bytes escaped as
 *       {@code 0x00 0xFF}, terminated by {@code 0x00 0x00}. The terminator
 *       prevents a shorter string from being a prefix of a longer one.</li>
 * </ul>
 */
public final class Property {

    public static final byte TAG_NULL    = 0x00;
    public static final byte TAG_LONG    = 0x01;
    public static final byte TAG_DOUBLE  = 0x02;
    public static final byte TAG_BOOLEAN = 0x03;
    public static final byte TAG_STRING  = 0x04;

    private static final byte ESC_BYTE  = 0x00;
    private static final byte ESC_MARK  = (byte) 0xFF;
    private static final byte[] TERMINATOR = {0x00, 0x00};

    private Property() {
        // utility class
    }

    /**
     * Encodes a property value into a self-describing, order-preserving byte
     * sequence suitable for use inside an index key.
     *
     * @param value the property value; supports Integer, Long, Double, Float,
     *              Boolean and String. {@code null} encodes as a single tag.
     * @return the encoded bytes
     * @throws IllegalArgumentException if the value type is unsupported
     */
    public static byte[] encodeValue(Object value) {
        if (value == null) {
            return new byte[]{TAG_NULL};
        }
        if (value instanceof Integer i) {
            return encodeLong(i.longValue());
        }
        if (value instanceof Long l) {
            return encodeLong(l);
        }
        if (value instanceof Float f) {
            return encodeDouble(f.doubleValue());
        }
        if (value instanceof Double d) {
            return encodeDouble(d);
        }
        if (value instanceof Boolean b) {
            return new byte[]{TAG_BOOLEAN, b ? (byte) 1 : (byte) 0};
        }
        if (value instanceof String s) {
            return encodeString(s);
        }
        throw new IllegalArgumentException("Unsupported property type: " + value.getClass());
    }

    /**
     * Encodes a long with the sign bit flipped so that two's-complement order
     * matches lexicographical order. This is what enables numeric range
     * queries (including negative numbers) against the index.
     */
    public static byte[] encodeLong(long v) {
        long shifted = v ^ 0x8000_0000_0000_0000L;
        ByteBuffer buf = ByteBuffer.allocate(1 + 8);
        buf.put(TAG_LONG);
        buf.putLong(shifted);
        return buf.array();
    }

    /**
     * Encodes a double using the standard "IEEE 754 to sortable bits" trick:
     * if the value is non-negative, flip just the sign bit; if it is
     * negative, flip every bit. The result, when compared as unsigned bytes,
     * matches the natural numeric order.
     */
    public static byte[] encodeDouble(double v) {
        long bits = Double.doubleToLongBits(v);
        long sortable = bits ^ ((bits >> 63) | 0x8000_0000_0000_0000L);
        ByteBuffer buf = ByteBuffer.allocate(1 + 8);
        buf.put(TAG_DOUBLE);
        buf.putLong(sortable);
        return buf.array();
    }

    /**
     * Encodes a string using a {@code 0x00} escape scheme followed by a
     * {@code 0x00 0x00} terminator. The terminator guarantees that for any
     * two distinct strings {@code a < b} lexicographically, their encoded
     * forms also satisfy {@code encode(a) < encode(b)} byte-wise, regardless
     * of one being a prefix of the other.
     */
    public static byte[] encodeString(String s) {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        // Worst case every byte is 0x00 and doubles in size; plus tag + terminator.
        ByteBuffer buf = ByteBuffer.allocate(1 + utf8.length * 2 + TERMINATOR.length);
        buf.put(TAG_STRING);
        for (byte b : utf8) {
            buf.put(b);
            if (b == ESC_BYTE) {
                buf.put(ESC_MARK);
            }
        }
        buf.put(TERMINATOR);
        byte[] out = new byte[buf.position()];
        System.arraycopy(buf.array(), 0, out, 0, out.length);
        return out;
    }

    /**
     * Normalizes a properties map into a HashMap copy safe for JSON
     * serialization. Kept as-is from the original implementation.
     */
    public static Map<String, Object> normalize(Map<String, Object> properties) {
        return new HashMap<>(properties);
    }
}
