# FEATURE-15 zora-promise-poc 实现方案

## 背景

`zora-promise-poc` 是一个 Java 实现的 Promise/A+ 规范的概念验证（POC）模块。该模块旨在探索 Java 异步编程模式，模拟 JavaScript 中广泛使用的 Promise 机制。

在现代 Java 开发中，异步编程非常重要。虽然 Java 提供了 `CompletableFuture`、`java.util.concurrent` 等强大的异步工具，但理解 Promise 的设计理念有助于：
- 更好地理解 JavaScript 异步编程
- 为将来可能的跨语言桥接提供基础
- 探索更优雅的异步 API 设计

## 范围

### 本次实现内容

1. **核心 Promise 类**
   - 创建 Promise 实例
   - 状态管理（PENDING、FULFILLED、REJECTED）
   - 异步执行器（Executor）

2. **实例方法**
   - `then(onFulfilled, onRejected)` - 注册回调
   - `catchError(onRejected)` - 仅注册错误回调
   - `finally_(onFinally)` - 注册无论成功失败都执行的回调

3. **静态工厂方法**
   - `Promise.resolve(value)` - 创建已解决的 Promise
   - `Promise.reject(reason)` - 创建已拒绝的 Promise
   - `Promise.all(promises)` - 等待所有 Promise 完成
   - `Promise.race(promises)` - 返回最先完成的结果
   - `Promise.allSettled(promises)` - 等待所有 Promise settled
   - `Promise.any(promises)` - 任一成功则成功

4. **微任务队列**
   - 单线程 Executor 模拟 JavaScript 微任务队列
   - 确保回调按正确顺序执行

### 不在本次范围内

- 不支持 Thenable 对象（仅支持本模块的 Promise）
- 不实现完整的 Promise/A+ 规范测试套件
- 不提供超时机制
- 不与 JDK CompletableFuture 互转

## 实现方案

### 1. 接口设计

```
PromiseCallback (Functional Interface)
├── invoke(Object value) -> Object

PromiseResolver (Functional Interface)
├── resolve(Object value) -> void

PromiseRejecter (Functional Interface)
├── reject(Object value) -> void

PromiseExecutor<T> (Functional Interface)
├── execute(PromiseResolver resolve, PromiseRejecter reject) -> void

PromiseState (Enum)
├── PENDING
├── FULFILLED
└── REJECTED
```

### 2. 核心类图

```
┌─────────────────────────────────────────────────────────────┐
│                      Promise<T>                              │
├─────────────────────────────────────────────────────────────┤
│ - microTaskQueue: LinkedBlockingQueue<Runnable>             │
│ - state: PromiseState                                        │
│ - value: Object                                              │
│ - settled: boolean                                           │
│ - onFulfilledCallbacks: List<PromiseCallback>               │
│ - onRejectedCallbacks: List<PromiseCallback>                │
├─────────────────────────────────────────────────────────────┤
│ + Promise(executor: PromiseExecutor<T>)                     │
│ + static resolve(value): Promise<T>                         │
│ + static reject(reason): Promise<T>                         │
│ + static all(promises: List): Promise<List<?>>              │
│ + static race(promises: List): Promise<Object>              │
│ + static allSettled(promises: List): Promise<List<?>>       │
│ + static any(promises: List): Promise<Object>               │
│ + then(onFulfilled, onRejected): Promise<?>                 │
│ + then(onFulfilled): Promise<?>                             │
│ + catchError(onRejected): Promise<?>                        │
│ + finally_(onFinally): Promise<?>                           │
│ - doResolve(value): void                                     │
│ - doReject(reason): void                                    │
│ - fulfill(value): void                                      │
│ - executeCallbacks(callbacks, value): void                  │
│ - scheduleMicroTask(task): void                             │
│ + static shutdown(): void                                    │
└─────────────────────────────────────────────────────────────┘
```

### 3. 状态转换图

```
                    ┌──────────────────────┐
                    │                      │
                    │    ┌────────────┐    │
         ┌──────────│───▶│   PENDING  │◀───┴──────────┐
         │          │    └────────────┘               │
         │          │         │                       │
         │          │    resolve()               reject()
         │          │         │                       │
         │          │         ▼                       ▼
         │          │    ┌────────────┐      ┌────────────┐
         │          └───│ FULFILLED  │      │ REJECTED   │
                    │    └────────────┘      └────────────┘
                    │         │                       │
                    │         │  只能转换一次          │
                    └─────────┴───────────────────────┘
```

### 4. 回调执行流程

#### 4.1 then 方法执行流程

```
promise.then(onFulfilled, onRejected)
         │
         ▼
    ┌─────────────────┐
    │   创建新Promise  │
    │  nextPromise    │
    └─────────────────┘
         │
    ┌────┴────────────────┐
    │ switch (this.state) │
    └────┬────────────────┘
         │
    ┌────┼────────────────────────────────┐
    │    │                                │
    ▼    ▼                                ▼
PENDING   FULFILLED                  REJECTED
    │         │                            │
    ▼         ▼                            ▼
┌─────────┐ ┌─────────┐                ┌─────────┐
│ 添加到  │ │ 异步调度 │                │ 异步调度 │
│ 回调队列 │ │ 执行回调 │                │ 执行回调 │
└─────────┘ └─────────┘                └─────────┘
    │
    ▼ (当 Promise 被 resolve/reject 时)
┌─────────────────────────────────┐
│ 执行回调队列中的所有回调        │
│ - 每个回调包装成微任务          │
│ - 调用 onFulfilled.invoke(value)│
│ - 返回值传给 nextPromise        │
│   -> doResolve(result)          │
└─────────────────────────────────┘
```

#### 4.2 链式调用流程

```java
promise
    .then(v1 -> transform(v1))  // Step 1
    .then(v2 -> transform(v2))  // Step 2
    .then(v3 -> end);           // Step 3
```

```
promise (P1)
    │
    ├── 创建 P2 (P1.then 返回)
    │       └── P2 的 onFulfilledCallbacks = [callback1]
    │
    ├── P1.resolve(10)
    │       └── 执行 callback1(10) -> 返回 20
    │             └── P2.doResolve(20)
    │                   └── P2.state = FULFILLED
    │                   └── P2.value = 20
    │                   └── 异步执行 P2 的回调
    │
    └── P2 (P2 是 P1.then 返回的 Promise)
            │
            ├── 创建 P3 (P2.then 返回)
            │       └── P3 的 onFulfilledCallbacks = [callback2]
            │
            ├── P2 异步执行 callback2(20) -> 返回 25
            │       └── P3.doResolve(25)
            │
            └── P3 (P2.then 返回的 Promise)
                    │
                    └── callback3(25) -> 执行最终逻辑
```

### 5. 微任务队列实现

```java
// 单线程 Executor 模拟微任务队列
private static final ExecutorService microTaskExecutor = 
    Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Promise-microtask");
        t.setDaemon(true);
        return t;
    });

private final LinkedBlockingQueue<Runnable> microTaskQueue = new LinkedBlockingQueue<>();

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
```

### 6. Promise.all 实现

```java
public static Promise<List<?>> all(List<? extends Promise<?>> promises) {
    return new Promise<>((resolve, reject) -> {
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
            }, reason -> {
                reject.reject(reason);
                return null;
            });
        }
    });
}
```

### 7. Promise.race 实现

```java
public static Promise<Object> race(List<? extends Promise<?>> promises) {
    return new Promise<>((resolve, reject) -> {
        if (promises == null || promises.isEmpty()) {
            return;  // 空数组永远 pending
        }

        // 确保只有第一个结果被采用
        final boolean[] settled = {false};

        for (Promise<?> promise : promises) {
            promise.then(value -> {
                if (!settled[0]) {
                    settled[0] = true;
                    resolve.resolve(value);
                }
                return null;
            }, reason -> {
                if (!settled[0]) {
                    settled[0] = true;
                    reject.reject(reason);
                }
                return null;
            });
        }
    });
}
```

## 使用示例

### 基本用法

```java
// 创建 Promise
Promise<Integer> promise = new Promise<>((resolve, reject) -> {
    // 模拟异步操作
    new Thread(() -> {
        try {
            Thread.sleep(1000);
            resolve.resolve(42);
        } catch (InterruptedException e) {
            reject.reject(e);
        }
    }).start();
});

// 注册回调
promise.then(value -> {
    System.out.println("Value: " + value);  // 1秒后输出: Value: 42
    return null;
}).catchError(reason -> {
    System.out.println("Error: " + reason);
});
```

### 链式调用

```java
new Promise<>((resolve, reject) -> resolve.resolve(10))
    .then(value -> {
        System.out.println("Step 1: " + value);  // Step 1: 10
        return (Integer) value * 2;               // 返回 20
    })
    .then(value -> {
        System.out.println("Step 2: " + value);  // Step 2: 20
        return (Integer) value + 5;               // 返回 25
    })
    .then(value -> {
        System.out.println("Step 3: " + value);  // Step 3: 25
        return null;
    });
```

### Promise.all

```java
List<Promise<?>> promises = Arrays.asList(
    Promise.resolve(1),
    Promise.resolve(2),
    Promise.resolve(3)
);

Promise.all(promises).then(result -> {
    System.out.println(result);  // [1, 2, 3]
    return null;
});
```

### Promise.race

```java
Promise<String> fast = new Promise<>((resolve, reject) -> {
    resolve.resolve("Fast");
});

Promise<String> slow = new Promise<>((resolve, reject) -> {
    Thread.sleep(50);
    resolve.resolve("Slow");
});

Promise.race(Arrays.asList(fast, slow)).then(result -> {
    System.out.println("Winner: " + result);  // Winner: Fast
    return null;
});
```

## 文件结构

```
zora-promise-poc/
├── pom.xml
├── metadata/
│   └── metadata.json
└── src/
    ├── main/
    │   ├── java/
    │   │   └── top/ilovemyhome/zora/poc/async/promise/
    │   │       ├── Promise.java              # 主实现
    │   │       ├── PromiseState.java         # 状态枚举
    │   │       ├── PromiseExecutor.java      # 执行器接口
    │   │       ├── PromiseResolver.java      # resolve 函数接口
    │   │       ├── PromiseRejecter.java      # reject 函数接口
    │   │       ├── PromiseCallback.java      # 回调接口
    │   │       └── PromiseConsumer.java      # 消费者接口
    │   └── resources/
    └── test/
        ├── java/
        │   └── top/ilovemyhome/zora/poc/async/promise/
        │       └── PromiseTest.java          # 测试类
        └── resources/
            └── simplelogger.properties
```

## 测试覆盖

| 测试用例 | 描述 |
|---------|------|
| testBasicResolve | 测试基本的 resolve 功能 |
| testBasicReject | 测试基本的 reject 功能 |
| testChaining | 测试链式调用 |
| testStaticResolve | 测试 Promise.resolve 静态方法 |
| testStaticReject | 测试 Promise.reject 静态方法 |
| testPromiseAll | 测试 Promise.all 全部成功 |
| testPromiseRace | 测试 Promise.race 竞速 |
| testPromiseAllSettled | 测试 Promise.allSettled |
| testPromiseAny | 测试 Promise.any 成功情况 |
| testPromiseAllWithRejection | 测试 Promise.all 失败情况 |
| testFinally | 测试 finally 回调 |

## 技术要点

### 1. 防止多次 Settle
使用 `settled` 标志确保 Promise 只能从 PENDING 状态转换一次到 FULFILLED 或 REJECTED。

### 2. 回调值传递
每个 `then` 调用返回新的 Promise，前一个 Promise 的回调返回值传递给下一个 Promise。

### 3. 微任务队列
使用单线程 Executor 确保所有回调按队列顺序异步执行，模拟 JavaScript 微任务行为。

### 4. 错误冒泡
如果没有为 `then` 提供 `onRejected` 回调，错误会自动传递到下一个 Promise。

## 未来扩展

1. **Thenable 支持** - 支持 Duck Typing 的 thenable 对象
2. **Promise/A+ 测试** - 通过官方测试套件
3. **超时机制** - 添加 `timeout` 方法
4. **静态方法扩展** - `Promise.allSettled` 的更多用法
5. **与 CompletableFuture 互转** - 提供桥接方法