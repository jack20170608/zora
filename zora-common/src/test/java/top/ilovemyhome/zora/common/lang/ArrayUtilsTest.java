package top.ilovemyhome.zora.common.lang;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ArrayUtilsTest {

    @Test
    public void testAsArray() {
        Integer[] integers = ArrayUtils.asArray(1, 2, 3);
        assertArrayEquals(new Integer[]{1, 2, 3}, integers);
    }

    @Test
    public void testAsArrayWithClass() {
        Integer[] integers = ArrayUtils.asArray(Integer.class, List.of(1, 2, 3));
        assertArrayEquals(new Integer[]{1, 2, 3}, integers);
    }

    @Test
    public void testAsArrayWithClassAndCollection() {
        Integer[] integers = ArrayUtils.asArray(Integer.class,null);
        assertArrayEquals(null, integers);
    }
}
