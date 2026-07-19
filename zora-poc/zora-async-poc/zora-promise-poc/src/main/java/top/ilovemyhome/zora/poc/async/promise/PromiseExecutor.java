package top.ilovemyhome.zora.poc.async.promise;

/**
 * Promise执行器接口
 * 对应JavaScript new Promise((resolve, reject) => {...}) 中的执行器函数
 */
@FunctionalInterface
public interface PromiseExecutor<T> {
    /**
     * 执行器函数
     * @param resolve 处理成功结果的函数
     * @param reject 处理失败原因的函数
     */
    void execute(PromiseResolver resolve, PromiseRejecter reject);
}