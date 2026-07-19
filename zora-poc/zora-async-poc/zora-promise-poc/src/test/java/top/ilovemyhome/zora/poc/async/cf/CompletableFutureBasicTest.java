package top.ilovemyhome.zora.poc.async.cf;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exploratory test for Java CompletableFuture functionality.
 */
class CompletableFutureBasicTest {

    @Test
    void testSupplyAsync() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "Hello CompletableFuture");
        String result = future.get();
        assertThat(result).isEqualTo("Hello CompletableFuture");
    }

    @Test
    void testThenApply() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "hello")
                .thenApply(String::toUpperCase);
        String result = future.get();
        assertThat(result).isEqualTo("HELLO");
    }

    @Test
    void testThenCompose() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "hello")
                .thenCompose(s -> CompletableFuture.supplyAsync(() -> s + " world"));
        String result = future.get();
        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void testThenCombine() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> "Hello");
        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> "World");

        CompletableFuture<String> combined = future1.thenCombine(future2, (s1, s2) -> s1 + " " + s2);
        String result = combined.get();
        assertThat(result).isEqualTo("Hello World");
    }

    @Test
    void testExceptionHandling() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Something went wrong");
        });
        CompletableFuture<String> handled = future.exceptionally(ex -> "Default value: " + ex.getMessage());

        String result = handled.get();
        assertThat(result).contains("Default value:");
    }

    @Test
    void testAllOf() throws ExecutionException, InterruptedException {
        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> "Result1");
        CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> "Result2");
        CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> "Result3");

        CompletableFuture<Void> allOf = CompletableFuture.allOf(f1, f2, f3);
        allOf.get();

        assertThat(f1.get()).isEqualTo("Result1");
        assertThat(f2.get()).isEqualTo("Result2");
        assertThat(f3.get()).isEqualTo("Result3");
    }

    @Test
    void testAnyOf() throws ExecutionException, InterruptedException {
        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return "Result1";
        });
        CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> "Result2");

        CompletableFuture<Object> anyOf = CompletableFuture.anyOf(f1, f2);
        Object result = anyOf.get();
        assertThat(result).isEqualTo("Result2");
    }
}
