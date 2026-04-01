package top.ilovemyhome.zora.common.lang;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ArrayUtils {

    public static <T> List<T> toList(T[] input) {
        if (Objects.isNull(input)){
            return null;
        }else {
            return Stream.of(input).toList();
        }
    }

    public static <T> Set<T> toSet(T[] input) {
        if (Objects.isNull(input)){
            return null;
        }else {
            return Stream.of(input).collect(Collectors.toSet());
        }
    }

    public static <T> T[] asArray(T... elements) {
        return elements;
    }

    public static <T> T[] asArray(Class<T> clazz, Collection<T> elements) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }
        if (elements == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T[] array = (T[]) java.lang.reflect.Array.newInstance(clazz, elements.size());
        return elements.toArray(array);
    }

    private ArrayUtils() {
    }
}
