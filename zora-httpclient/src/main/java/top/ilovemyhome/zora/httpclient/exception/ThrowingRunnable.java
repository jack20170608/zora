package top.ilovemyhome.zora.httpclient.exception;

@FunctionalInterface
public interface ThrowingRunnable<T extends Throwable> {

    void apply() throws T;

    static <T extends Throwable> Runnable unchecked(ThrowingRunnable<T> consumer) {
        return () -> {
            try {
                consumer.apply();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        };
    }
}
