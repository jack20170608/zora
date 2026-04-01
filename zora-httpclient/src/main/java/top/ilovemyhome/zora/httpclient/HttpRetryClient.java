package top.ilovemyhome.zora.httpclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public final class HttpRetryClient {

    public static Builder builder(HttpClient client) {
        return new Builder(client);
    }

    private final HttpClient httpClient;
    private final int maxAttempts;
    private final IntPredicate successPredicate;
    private final EvtConsumer evtConsumer;
    private final Executor retryExecutor;
    private final Executor delayExecutor;

    public HttpRetryClient(HttpClient client, int maxAttempts, IntPredicate successPredicate
        , EvtConsumer evtConsumer, Executor retryExecutor, Duration retryDelay) {
        this.httpClient = requireNonNull(client, "HttpClient must not be null");
        assertArgument(maxAttempts, (Predicate<Integer>) o -> o > 0, "Invalid max attempts %s, must be greater than zero", maxAttempts);
        this.maxAttempts = maxAttempts;
        this.successPredicate = requireNonNull(successPredicate, "Success predicate must not be null");
        this.evtConsumer = requireNonNull(evtConsumer, "EvtConsumer must not be null");
        this.retryExecutor = requireNonNull(retryExecutor, "Executor must not be null");
        this.delayExecutor = Objects.nonNull(retryDelay) ? CompletableFuture.delayedExecutor(retryDelay.toMillis(), TimeUnit.MILLISECONDS, retryExecutor) : retryExecutor;
    }

    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, final HttpResponse.BodyHandler<T> bodyHandler) {
        return retryAsync(request, () -> httpClient.sendAsync(request, bodyHandler), maxAttempts);
    }

    private <T> CompletableFuture<HttpResponse<T>> retryAsync(final HttpRequest req
        , final Supplier<CompletableFuture<HttpResponse<T>>> action, final int max) {
        final var future = new CompletableFuture<HttpResponse<T>>();
        action.get()
            .handleAsync((newResp, ex) -> {
                if (ex != null) {
                    future.completeExceptionally(ex);
                } else {
                    retryLogic(req, newResp, max, 1, action, future);
                }
                return null;
            }, retryExecutor);
        return future;
    }

    private <T> void retryLogic(final HttpRequest req,
                                final HttpResponse<T> resp,
                                final int max,
                                final int count,
                                final Supplier<CompletableFuture<HttpResponse<T>>> action,
                                final CompletableFuture<HttpResponse<T>> future) {
        if (count >= max || successPredicate.test(resp.statusCode())) {
            evtConsumer.onAttempt(count, true, req.uri(), req.method(), resp.statusCode());
            future.complete(resp);
        } else {
            evtConsumer.onAttempt(count, false, req.uri(), req.method(), resp.statusCode());
            action.get().handleAsync((newResp, ex) -> {
                if (ex != null) {
                    future.completeExceptionally(ex);
                } else {
                    retryLogic(req, newResp, max, count + 1, action, future);
                }
                return null;
            }, delayExecutor);
        }
    }

    public static final class Builder {
        private final HttpClient httpClient;
        private int maxAttempts = 5;

        private EvtConsumer evtConsumer = (attempt, completed, uri, method, status) -> {
        };

        private Executor retryExecutor = ForkJoinPool.commonPool();

        private Duration delay = Duration.ofMillis(100);

        private IntPredicate successPredicate = status -> status < 500;

        private Builder(final HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        public Builder withMaxAttempts(final int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder withSuccessStatus(int[] codes) {
            Set<Integer> successStatus = Arrays.stream(codes).boxed().collect(Collectors.toSet());
            this.successPredicate = successStatus::contains;
            return this;
        }

        public Builder withSuccessStatusRange(int from, int to) {
            assertArgument(from, to, (BiPredicate<Integer, Integer>) ((f, t) -> f < t)
                , "Invalid range %s is greater than %s", from, to);
            this.successPredicate = status -> status >= from && status <= to;
            return this;
        }

        public Builder withSuccessPredicate(IntPredicate predicate) {
            this.successPredicate = predicate;
            return this;
        }

        public Builder withEvtConsumer(final EvtConsumer evtConsumer) {
            this.evtConsumer = evtConsumer;
            return this;
        }

        public Builder withLogConsumer(final Consumer<String> msgConsumer) {
            return withEvtConsumer(new EvtConsumer() {
                @Override
                public void onAttempt(int attempt, boolean completed, URI uri, String method, int status) {
                    final var msg = "# " + attempt + " " + (completed ? "completed" : "retrying") + " " + uri + " " + method + " " + status;
                    msgConsumer.accept(msg);
                }
            });
        }

        public Builder withRetryOnlyLogConsumer(final Consumer<String> msgConsumer) {
            return withEvtConsumer(new EvtConsumer() {
                @Override
                public void onAttempt(int attempt, boolean completed, URI uri, String method, int status) {
                    if (!completed || attempt > 1) {
                        final var msg = "# " + attempt + " " + (completed ? "completed" : "retrying") + " " + uri + " " + method + " " + status;
                        msgConsumer.accept(msg);
                    }
                }
            });
        }

        public Builder withRetryExecutor(final Executor retryExecutor) {
            this.retryExecutor = retryExecutor;
            return this;
        }

        public Builder withDelay(final Duration delay) {
            this.delay = delay;
            return this;
        }

        public Builder withNoDelay() {
            return withDelay(null);
        }

        public HttpRetryClient build() {
            return new HttpRetryClient(httpClient, maxAttempts, successPredicate, evtConsumer, retryExecutor, delay);
        }
    }


    private static void assertArgument(Object arg1, Object arg2, BiPredicate biPredicate, String msgFormat, Object... msgArgs) {
        if (!biPredicate.test(arg1, arg2)) {
            throw new IllegalArgumentException(String.format(msgFormat, msgArgs));
        }
    }

    private static void assertArgument(Object arg1, Predicate predicate, String msgFormat, Object... msgArgs) {
        if (!predicate.test(arg1)) {
            throw new IllegalArgumentException(String.format(msgFormat, msgArgs));
        }
    }

    @FunctionalInterface
    public interface EvtConsumer {
        void onAttempt(int attempt, boolean completed, URI uri, String method, int status);
    }
}
