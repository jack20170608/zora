package top.ilovemyhome.zora.poc.async.promise;

/**
 * Promise失败原因消费者接口
 * 对应JavaScript Promise中reject函数
 */
@FunctionalInterface
public interface PromiseRejecter {
    /**
     * 拒绝Promise
     * @param reason 拒绝原因
     */
    void reject(Object reason);
}