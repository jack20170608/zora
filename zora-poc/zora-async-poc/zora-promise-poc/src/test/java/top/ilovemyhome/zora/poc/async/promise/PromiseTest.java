package top.ilovemyhome.zora.poc.async.promise;

import java.util.Arrays;
import java.util.List;

/**
 * Promise功能测试类
 */
public class PromiseTest {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Promise Basic Test ===\n");

        testBasicResolve();
        Thread.sleep(100);

        testBasicReject();
        Thread.sleep(100);

        testChaining();
        Thread.sleep(100);

        testStaticResolve();
        Thread.sleep(100);

        testStaticReject();
        Thread.sleep(100);

        testPromiseAll();
        Thread.sleep(100);

        testPromiseRace();
        Thread.sleep(100);

        testPromiseAllSettled();
        Thread.sleep(100);

        testPromiseAny();
        Thread.sleep(100);

        testPromiseAllWithRejection();
        Thread.sleep(100);

        testFinally();
        Thread.sleep(100);

        // 关闭线程池
        Promise.shutdown();

        System.out.println("\n=== All tests completed ===");
    }

    /**
     * 测试基本的resolve功能
     */
    private static void testBasicResolve() {
        System.out.println("--- Test: Basic Resolve ---");
        Promise<Integer> promise = new Promise<>((resolve, reject) -> {
            resolve.resolve(42);
        });

        promise.then(value -> {
            System.out.println("Resolved value: " + value);
            return null;
        });
    }

    /**
     * 测试基本的reject功能
     */
    private static void testBasicReject() {
        System.out.println("\n--- Test: Basic Reject ---");
        Promise<Integer> promise = new Promise<>((resolve, reject) -> {
            reject.reject("Error occurred");
        });

        promise.catchError(reason -> {
            System.out.println("Rejected reason: " + reason);
            return null;
        });
    }

    /**
     * 测试链式调用
     */
    private static void testChaining() {
        System.out.println("\n--- Test: Chaining ---");
        Promise<Integer> promise = new Promise<>((resolve, reject) -> {
            resolve.resolve(10);
        });

        promise
            .then(value -> {
                System.out.println("Step 1: " + value);
                return (Integer) value * 2;  // 20
            })
            .then(value -> {
                System.out.println("Step 2: " + value);
                return (Integer) value + 5;  // 25
            })
            .then(value -> {
                System.out.println("Step 3: " + value);
                return null;
            });
    }

    /**
     * 测试Promise.resolve静态方法
     */
    private static void testStaticResolve() {
        System.out.println("\n--- Test: Promise.resolve ---");
        Promise<String> promise = Promise.resolve("Hello");

        promise.then(value -> {
            System.out.println("Static resolve: " + value);
            return null;
        });
    }

    /**
     * 测试Promise.reject静态方法
     */
    private static void testStaticReject() {
        System.out.println("\n--- Test: Promise.reject ---");
        Promise<String> promise = Promise.reject("Static error");

        promise.catchError(reason -> {
            System.out.println("Static reject: " + reason);
            return null;
        });
    }

    /**
     * 测试Promise.all
     */
    @SuppressWarnings("unchecked")
    private static void testPromiseAll() {
        System.out.println("\n--- Test: Promise.all ---");

        List<Promise<?>> promises = Arrays.asList(
            Promise.resolve(1),
            Promise.resolve(2),
            Promise.resolve(3)
        );

        Promise.all(promises).then(result -> {
            System.out.println("Promise.all result: " + result);
            return null;
        });
    }

    /**
     * 测试Promise.race
     */
    @SuppressWarnings("unchecked")
    private static void testPromiseRace() {
        System.out.println("\n--- Test: Promise.race ---");

        Promise<String> fast = new Promise<>((resolve, reject) -> {
            resolve.resolve("Fast");
        });

        Promise<String> slow = new Promise<>((resolve, reject) -> {
            // 延迟resolve
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            resolve.resolve("Slow");
        });

        List<Promise<?>> promises = Arrays.asList(fast, slow);
        Promise.race(promises).then(result -> {
            System.out.println("Promise.race winner: " + result);
            return null;
        });
    }

    /**
     * 测试Promise.allSettled
     */
    @SuppressWarnings("unchecked")
    private static void testPromiseAllSettled() {
        System.out.println("\n--- Test: Promise.allSettled ---");

        List<Promise<?>> promises = Arrays.asList(
            Promise.resolve(1),
            Promise.reject("Error"),
            Promise.resolve(3)
        );

        Promise.allSettled(promises).then(result -> {
            System.out.println("Promise.allSettled result: " + result);
            return null;
        });
    }

    /**
     * 测试Promise.any
     */
    @SuppressWarnings("unchecked")
    private static void testPromiseAny() {
        System.out.println("\n--- Test: Promise.any ---");

        List<Promise<?>> promises = Arrays.asList(
            Promise.reject("Error 1"),
            Promise.resolve("Success"),
            Promise.reject("Error 2")
        );

        Promise.any(promises).then(result -> {
            System.out.println("Promise.any result: " + result);
            return null;
        }).catchError(reason -> {
            System.out.println("Promise.any failed: " + reason);
            return null;
        });
    }

    /**
     * 测试Promise.all中有一个reject的情况
     */
    @SuppressWarnings("unchecked")
    private static void testPromiseAllWithRejection() {
        System.out.println("\n--- Test: Promise.all with rejection ---");

        List<Promise<?>> promises = Arrays.asList(
            Promise.resolve(1),
            Promise.reject("Error!"),
            Promise.resolve(3)
        );

        Promise.all(promises).then(result -> {
            System.out.println("Should not print: " + result);
            return null;
        }).catchError(reason -> {
            System.out.println("Promise.all caught error: " + reason);
            return null;
        });
    }

    /**
     * 测试finally
     */
    private static void testFinally() {
        System.out.println("\n--- Test: Finally ---");

        Promise<Integer> promise = new Promise<>((resolve, reject) -> {
            resolve.resolve(100);
        });

        promise
            .then(value -> {
                System.out.println("Resolved: " + value);
                return value;
            })
            .finally_(() -> {
                System.out.println("Finally executed!");
            })
            .then(value -> {
                System.out.println("After finally: " + value);
                return null;
            });
    }
}