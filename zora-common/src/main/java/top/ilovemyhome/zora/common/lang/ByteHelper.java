package top.ilovemyhome.zora.common.lang;

import java.io.*;
import java.nio.file.Files;

public final class ByteHelper {

    private ByteHelper() {
        // Prevent instantiation
    }

    public static byte[] getBytes(File file){
        if (file == null) {
            throw new IllegalArgumentException("File argument cannot be null.");
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (FileNotFoundException e) {
            String msg = "Unable to acquire InputStream for file [" + file + "]";
            throw new RuntimeException(msg, e);
        } catch (IOException e) {
            String msg = "Unable to read bytes from file [" + file + "]";
            throw new RuntimeException(msg, e);
        }
    }

    public static byte[] getBytes(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("InputStream argument cannot be null.");
        }
        final int bufferSize = 512;
        try (in; ByteArrayOutputStream out = new ByteArrayOutputStream(bufferSize)) {
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return out.toByteArray();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }



}
