package top.ilovemyhome.zora.common.lang;

import java.util.*;

public final class CollectionUtil {
    private CollectionUtil() {}

    public static boolean isEmpty(Collection<?> collection){
        return Optional.ofNullable(collection)
            .map(c -> c.isEmpty())
            .orElse(true);
    }

    public static <E> List<E> asList(E... elements) {
        if (elements == null || elements.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(elements);
    }

    public static <E> Set<E> asSet(E... elements) {
        if (elements == null || elements.length == 0) {
            return Collections.emptySet();
        }

        return new HashSet<>(Arrays.asList(elements));
    }
}
