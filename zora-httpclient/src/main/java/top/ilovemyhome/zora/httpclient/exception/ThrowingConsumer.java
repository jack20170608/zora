package top.ilovemyhome.zora.httpclient.exception;

import java.util.function.Consumer;

@FunctionalInterface
public interface ThrowingConsumer<E, T extends Throwable> {

    void apply(E e) throws T;

    static <E, T extends Throwable> Consumer<E> unchecked(ThrowingConsumer<E, T> consumer) {
        return e -> {
            try {
                consumer.apply(e);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        };
    }
}
