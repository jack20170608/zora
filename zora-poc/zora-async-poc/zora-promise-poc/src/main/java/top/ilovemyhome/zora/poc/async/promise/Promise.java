package top.ilovemyhome.zora.poc.async.promise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Java实现JavaScript的Promise功能
 *
 * 支持的功能：
 * - 实例方法: then, catch, finally
 * - 静态方法: resolve, reject, all, race, allSettled, any
 *
 * 用法示例：
 * <pre>
 * Promise<Integer> promise = new Promise<((resolve, reject) -> {
 *     resolve.resolve(42);
 * });
 * promise.then(value -> {
 *     System.out.println("Value: " + value);
 *     return value * 2;
 * }).then(value -> {
 *     System.out.println("Doubled: " + value);
 *     return null;
 * }).catchError(reason -> {
 *     System.out.println("Error: " + reason);
 * });
 * </pre>
 *
 * @param <T> Promise resolve的值类型
 */
public class Promise<T> {

    // 回调队列
    private final LinkedBlockingQueue<Runnable> microTaskQueue = new LinkedBlockingQueue<>();

    // Promise状态
    private PromiseState state = PromiseState.PENDING;

    // resolve的值或reject的原因
    private Object result;

    // 保存注册的成功回调
    private final List<PromiseConsumer<? super T>> onFulfilledCallbacks = new ArrayList<>();

    // 保存注册的失败回调
    private final List<PromiseRejecter> onRejectedCallbacks = new ArrayList<>();

    // 线程池（单线程模拟微任务队列）
    private static final ExecutorService microTaskExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Promise-microtask");
        t.setDaemon(true);
        return t;
    });

    /**
     * 创建一个新的Promise
     * @param executor 执行器函数
     */
    @SuppressWarnings("unchecked")
    public Promise(PromiseExecutor<T> executor) {
        if (executor == null) {
            throw new IllegalArgumentException("Executor cannot be null");
        }

        try {
            executor.execute(
                // resolve函数
                value -> resolve((T) value),
                // reject函数
                reason -> reject(reason)
            );
        } catch (Throwable e) {
            reject(e);
        }
    }

    /**
     * 私有构造函数，用于静态工厂方法
     */
    private Promise() {}

    /**
     * 私有构造函数，用于创建then返回的新Promise
     */
    private Promise(Promise<?> parent) {
        // 继承父Promise的状态
        this.state = parent.state;
        this.result = parent.result;
    }

    /**
     * 创建已解决的Promise（静态方法）
     * 对应JavaScript: Promise.resolve(value)
     */
    public static <T> Promise<T> resolve(Object value) {
        Promise<T> promise = new Promise<>();
        promise.state = PromiseState.FULFILLED;
        promise.result = value;
        return promise;
    }

    /**
     * 创建已拒绝的Promise（静态方法）
     * 对应JavaScript: Promise.reject(reason)
     */
    public static <T> Promise<T> reject(Object reason) {
        Promise<T> promise = new Promise<>();
        promise.state = PromiseState.REJECTED;
        promise.result = reason;
        return promise;
    }

    /**
     * 等待所有Promise完成（静态方法）
     * 对应JavaScript: Promise.all(promises)
     *
     * @param promises Promise数组
     * @return 所有Promise都成功时返回包含所有结果的List；任一失败时返回失败的Promise
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Promise<List<?>> all(List<? extends Promise<?>> promises) {
        return new Promise((resolve, reject) -> {
            if (promises == null || promises.isEmpty()) {
                resolve.resolve(Collections.emptyList());
                return;
            }

            int total = promises.size();
            AtomicInteger counter = new AtomicInteger(0);
            List<Object> results = Collections.synchronizedList(new ArrayList<>(total));

            // 预设结果列表的位置
            for (int i = 0; i < total; i++) {
                results.add(null);
            }

            for (int i = 0; i < total; i++) {
                final int index = i;
                Promise<?> promise = promises.get(i);

                promise.then(value -> {
                    results.set(index, value);
                    if (counter.incrementAndGet() == total) {
                        resolve.resolve(results);
                    }
                    return null;
                }).catchError(reason -> {
                    reject.resolve(reason);
                    return null;
                });
            }
        });
    }

    /**
     * Promise.race - 赛跑，最先settle的决定结果（静态方法）
     * 对应JavaScript: Promise.race(promises)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Promise<Object> race(List<? extends Promise<?>> promises) {
        return new Promise((resolve, reject) -> {
            if (promises == null || promises.isEmpty()) {
                // 空数组会永远pending，这是JavaScript的行为
                return;
            }

            for (Promise<?> promise : promises) {
                promise.then(value -> {
                    resolve.resolve(value);
                    return null;
                }).catchError(reason -> {
                    reject.resolve(reason);
                    return null;
                });
            }
        });
    }

    /**
     * Promise.allSettled - 等所有Promise settled（静态方法）
     * 对应JavaScript: Promise.allSettled(promises)
     *
     * @return 包含每个Promise状态的列表
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Promise<List<?>> allSettled(List<? extends Promise<?>> promises) {
        return new Promise((resolve, reject) -> {
            if (promises == null || promises.isEmpty()) {
                resolve.resolve(Collections.emptyList());
                return;
            }

            int total = promises.size();
            AtomicInteger counter = new AtomicInteger(0);
            List<Object> results = Collections.synchronizedList(new ArrayList<>(total));

            // 预设位置
            for (int i = 0; i < total; i++) {
                results.add(null);
            }

            for (int i = 0; i < total; i++) {
                final int index = i;
                Promise<?> promise = promises.get(i);

                // 每个Promise无论成功还是失败都记录结果
                promise.then(value -> {
                    results.set(index, new PromiseResult(PromiseState.FULFILLED, value));
                    if (counter.incrementAndGet() == total) {
                        resolve.resolve(results);
                    }
                    return null;
                }).catchError(reason -> {
                    results.set(index, new PromiseResult(PromiseState.REJECTED, reason));
                    if (counter.incrementAndGet() == total) {
                        resolve.resolve(results);
                    }
                    return null;
                });
            }
        });
    }

    /**
     * Promise.any - 任一成功则成功，全部失败则失败（静态方法）
     * 对应JavaScript: Promise.any(promises)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Promise<Object> any(List<? extends Promise<?>> promises) {
        return new Promise((resolve, reject) -> {
            if (promises == null || promises.isEmpty()) {
                // AggregateError
                reject.resolve(new Throwable("All promises were rejected"));
                return;
            }

            int total = promises.size();
            AtomicInteger counter = new AtomicInteger(0);

            for (Promise<?> promise : promises) {
                promise.then(value -> {
                    resolve.resolve(value);
                    return null;
                }).catchError(reason -> {
                    if (counter.incrementAndGet() == total) {
                        // 全部失败
                        reject.resolve(new Throwable("All promises were rejected"));
                    }
                    return null;
                });
            }
        });
    }

    // ==================== 实例方法 ====================

    /**
     * 注册fulfilled和rejected回调
     * 对应JavaScript: promise.then(onFulfilled, onRejected)
     *
     * @param onFulfilled 成功回调
     * @param onRejected 失败回调
     * @return 新的Promise（支持链式调用）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Promise<?> then(PromiseConsumer<? super T> onFulfilled, PromiseRejecter onRejected) {
        // 创建新的Promise
        Promise<?> nextPromise = new Promise<>(this);

        // 根据当前状态处理
        if (state == PromiseState.PENDING) {
            // pending状态，存储回调
            if (onFulfilled != null) {
                onFulfilledCallbacks.add(value -> {
                    try {
                        // 调用回调并处理返回值（可能是普通值或Promise）
                        Object result = onFulfilled.accept((T) value);
                        nextPromise.resolve(result);
                    } catch (Throwable e) {
                        nextPromise.reject(e);
                    }
                    return null;
                });
            }
            if (onRejected != null) {
                onRejectedCallbacks.add(reason -> {
                    try {
                        Object result = onRejected.accept(reason);
                        nextPromise.resolve(result);
                    } catch (Throwable e) {
                        nextPromise.reject(e);
                    }
                    return null;
                });
            } else {
                // 如果没有onRejected，将错误传递给下一个Promise
                onRejectedCallbacks.add(reason -> {
                    nextPromise.reject(reason);
                    return null;
                });
            }
        } else if (state == PromiseState.FULFILLED) {
            // 已经fulfilled，异步执行onFulfilled
            if (onFulfilled != null) {
                scheduleMicroTask(() -> {
                    try {
                        Object result = onFulfilled.accept((T) result);
                        nextPromise.resolve(result);
                    } catch (Throwable e) {
                        nextPromise.reject(e);
                    }
                });
            } else {
                // 没有onFulfilled，直接传递值
                nextPromise.resolve(result);
            }
        } else if (state == PromiseState.REJECTED) {
            // 已经rejected，异步执行onRejected
            if (onRejected != null) {
                scheduleMicroTask(() -> {
                    try {
                        Object result = onRejected.accept(result);
                        nextPromise.resolve(result);
                    } catch (Throwable e) {
                        nextPromise.reject(e);
                    }
                });
            } else {
                // 没有onRejected，传递错误
                nextPromise.reject(result);
            }
        }

        return nextPromise;
    }

    /**
     * 只注册rejected回调（简写）
     * 对应JavaScript: promise.catch(onRejected)
     */
    public Promise<?> catchError(PromiseRejecter onRejected) {
        return then(null, onRejected);
    }

    /**
     * 注册无论成功还是失败都会执行的回调
     * 对应JavaScript: promise.finally(onFinally)
     *
     * @param onFinally 无论成功失败都执行的回调
     * @return 新的Promise
     */
    public Promise<?> finally_(Runnable onFinally) {
        return then(value -> {
            if (onFinally != null) {
                onFinally.run();
            }
            return value;
        }, reason -> {
            if (onFinally != null) {
                onFinally.run();
            }
            throw new RuntimeException(String.valueOf(reason));
        });
    }

    // ==================== 内部方法 ====================

    /**
     * 解决Promise（内部使用）
     */
    @SuppressWarnings("unchecked")
    private void resolve(T value) {
        if (state != PromiseState.PENDING) {
            return;
        }

        // 处理Promise resolution procedure
        // 如果value是Promise或thenable，需要等待它settle
        if (isPromiseLike(value)) {
            Promise<?> nestedPromise = (Promise<?>) value;
            nestedPromise.then(v -> {
                fulfill(v);
                return null;
            }).catchError(e -> {
                reject(e);
                return null;
            });
        } else {
            fulfill(value);
        }
    }

    /**
     * 拒绝Promise（内部使用）
     */
    @SuppressWarnings("unchecked")
    private void reject(Object reason) {
        if (state != PromiseState.PENDING) {
            return;
        }
        state = PromiseState.REJECTED;
        this.result = reason;

        // 执行onRejected回调
        executeCallbacks(onRejectedCallbacks, reason);
    }

    /**
     * 完成Promise（内部使用）
     */
    private void fulfill(Object value) {
        if (state != PromiseState.PENDING) {
            return;
        }
        state = PromiseState.FULFILLED;
        this.result = value;

        // 执行onFulfilled回调
        executeCallbacks(onFulfilledCallbacks, value);
    }

    /**
     * 检查值是否是Promise或thenable
     */
    private boolean isPromiseLike(Object value) {
        if (value instanceof Promise) {
            return true;
        }
        // 可以扩展支持thenable对象
        return false;
    }

    /**
     * 执行回调队列
     */
    private void executeCallbacks(List<? extends java.util.function.Consumer<Object>> callbacks, Object value) {
        for (java.util.function.Consumer<Object> callback : callbacks) {
            final Object finalValue = value;
            scheduleMicroTask(() -> callback.accept(finalValue));
        }
    }

    /**
     * 调度微任务
     */
    private void scheduleMicroTask(Runnable task) {
        microTaskQueue.offer(task);
        if (!microTaskExecutor.isShutdown()) {
            microTaskExecutor.submit(() -> {
                Runnable r;
                while ((r = microTaskQueue.poll()) != null) {
                    try {
                        r.run();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });
        }
    }

    /**
     * 获取当前状态（用于调试）
     */
    public PromiseState getState() {
        return state;
    }

    /**
     * 获取结果（用于调试）
     */
    public Object getResult() {
        return result;
    }

    /**
     * 关闭线程池（应在程序结束时调用）
     */
    public static void shutdown() {
        microTaskExecutor.shutdown();
        try {
            if (!microTaskExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                microTaskExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            microTaskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Promise.allSettled返回的结果包装类
     */
    public static class PromiseResult {
        private final PromiseState status;
        private final Object value;

        public PromiseResult(PromiseState status, Object value) {
            this.status = status;
            this.value = value;
        }

        public PromiseState getStatus() {
            return status;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "PromiseResult{status=" + status + ", value=" + value + "}";
        }
    }
}
