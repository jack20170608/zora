package top.ilovemyhome.zora.common.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ResourceUtil {

    public ResourceUtil() {
    }

    public static InputStream getClasspathResourceAsStream(final String path) {
        return ResourceUtil.class.getClassLoader().getResourceAsStream(path);
    }

    public static String getClasspathResourceAsString(final String classFilePath) {
        try {
            return Files.readString(getClassPathFile(classFilePath));
        }catch (IOException e) {
            throw new RuntimeException("File read failure.", e);
        }
    }

    public static List<String> getClasspathResourceAsStringList(final String classFilePath) {
        try {
            return Files.readAllLines(getClassPathFile(classFilePath));
        }catch (IOException e) {
            throw new RuntimeException("File read failure.", e);
        }
    }

    public static Path getClassPathFile(String filePath) {
        try {
            ClassLoader classLoader = ResourceUtil.class.getClassLoader();
            URL resource = classLoader.getResource(filePath);
            if (Objects.isNull(resource)) {
                throw new IllegalArgumentException("Cannot find the resource!");
            } else {
                return Path.of(resource.toURI());
            }
        }catch (URISyntaxException e){
            throw new IllegalArgumentException("Cannot find the resource!");
        }
    }
}
