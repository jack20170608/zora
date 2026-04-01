package top.ilovemyhome.zora.common.codec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class DigestUtils {

    public static String sha256Hex(String input) {
        if (input == null) {
            return null;
        }
        try {
            // Create a SHA - 256 digest instance
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Convert the input string to a byte array
            byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
            // Update the digest
            byte[] hash = digest.digest(inputBytes);
            // Convert the byte array to a hexadecimal string
            return Hex.encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // This should never happen as SHA - 256 is a standard algorithm
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
