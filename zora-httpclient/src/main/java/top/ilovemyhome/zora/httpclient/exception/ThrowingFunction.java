package top.ilovemyhome.zora.httpclient.exception;

import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<I, O , T extends Throwable> {

    O apply(I input) throws T;

    static <I, O, T extends Throwable> Function<I, O> unchecked(ThrowingFunction<I, O, T> consumer) {
        return e -> {
            try {
                return consumer.apply(e);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        };
    }
}
