package top.ilovemyhome.zora.common.validate;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class ValidateHelper {

    public static void notNull(Object object, String message) {
        if (Objects.isNull(object)) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void notEmpty(String string, String message) {
        notNull(string, message);
        if (string.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void notBlank(String string, String message) {
        notEmpty(string, message);
        if (string.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void notEmpty(Collection<?> collection, String message) {
        notNull(collection, message);
        if (collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void notEmpty(Map<?, ?> map, String message) {
        notNull(map, message);
        if (map.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    public static <T> void notEmpty(T[] array, String message) {
        notNull(array, message);
        if (array.length == 0) {
            throw new IllegalArgumentException(message);
        }
    }

    public static <T> void in(T t, Collection<T> set, String message) {
        notNull(t, "The target object should not null.");
        notEmpty(set, "The target object set should not empty.");
        if (!set.contains(t)) {
            throw new IllegalArgumentException(message);
        }
    }

    public static <T> void in(T obj, T [] set, String message) {
        notNull(obj, "The target object should not null.");
        notEmpty(set, "The target object collection should not empty.");
        if (!Set.of(set).contains(obj)) {
            throw new IllegalArgumentException(message);
        }
    }

    public static void isDirectoryExists(Path dirPath, String message) {
        notNull(dirPath, "The target path should not null.");
        if (dirPath.toFile().exists() || !dirPath.toFile().isDirectory()) {
            throw new IllegalArgumentException("The target path is not a directory.");
        }
    }

    public static <T> void assertArgument(T t, Predicate<T> predicate, String message) {
        notNull(t, "The target object should not null.");
        notNull(predicate, "The target object predicate should not null.");
        if (!predicate.test(t)){
            throw new IllegalArgumentException(message);
        }
    }

    private ValidateHelper() {
    }
}
