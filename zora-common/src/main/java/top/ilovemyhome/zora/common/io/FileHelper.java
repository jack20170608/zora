package top.ilovemyhome.zora.common.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class FileHelper{

    public static List<String> searchWithWildcard(Path rootDir, String pattern) throws IOException {
        List<String> matchesList = new ArrayList<>();
        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {
                FileSystem fs = FileSystems.getDefault();
                PathMatcher matcher = fs.getPathMatcher(pattern);
                Path name = file.getFileName();
                if (matcher.matches(name)) {
                    matchesList.add(name.toString());
                }
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(rootDir, matcherVisitor);
        return matchesList;
    }

    public static void deleteWithPredicate(Path sourcePath, Predicate<String> predicate){
        boolean pathExists = Files.exists(sourcePath) && Files.isDirectory(sourcePath);
        if (pathExists){
            try (Stream<Path> stream = Files.list(sourcePath)) {
                stream.map(p -> p.getFileName().toString())
                    .filter(predicate)
                    .forEach(f -> {
                        try {
                            Files.deleteIfExists(sourcePath.resolve(f));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            } catch (IOException e) {
                logger.warn("File list failure.", e);
                throw new RuntimeException(e);
            }
        }else {
            throw new IllegalStateException("Source path is not exists or not a directory.");
        }
    }

    private FileHelper() {
    }

    private static final Logger logger = LoggerFactory.getLogger(FileHelper.class);
}
