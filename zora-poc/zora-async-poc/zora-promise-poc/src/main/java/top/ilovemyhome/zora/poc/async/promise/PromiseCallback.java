package top.ilovemyhome.zora.poc.async.promise;

/**
 * Promise回调函数接口
 * 用于onFulfilled和onRejected回调
 */
@FunctionalInterface
public interface PromiseCallback {
    /**
     * 执行回调
     * @param value Promise的值（成功时为resolve值，失败时为reject原因）
     * @return 新的值（可以是新的Promise或普通值）
     */
    Object invoke(Object value) throws Exception;
}