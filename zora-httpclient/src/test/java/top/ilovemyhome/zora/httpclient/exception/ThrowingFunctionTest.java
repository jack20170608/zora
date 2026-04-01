package top.ilovemyhome.zora.httpclient.exception;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ThrowingFunctionTest{

    @Test
    public void testThrowingConsumer() {
        Path source = Path.of("/tmp", "foo");
        assertThrows(RuntimeException.class, () -> {
            readFileFunc.apply(source);
        }, "Not throw runtime exception");
    }

    @Test
    public void testThrowingRunnable() {
        assertThrows(RuntimeException.class, () -> {
            badRun.run();
        }, "Not throw runtime exception");
    }

    private Function<Path, String> readFileFunc = ThrowingFunction.unchecked(Files::readString);
    private Runnable badRun = ThrowingRunnable.unchecked(() -> {
        int total = 10;
        int result;
        for (int i = 0; i <  1; i++) {
            result = total / i;
        }
    });
}
