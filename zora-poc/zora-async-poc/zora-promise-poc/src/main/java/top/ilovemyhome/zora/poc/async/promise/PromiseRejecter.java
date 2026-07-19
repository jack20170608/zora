package top.ilovemyhome.zora.poc.async.promise;

/**
 * Promise失败原因消费者接口
 * 对应JavaScript Promise中onRejected函数
 */
@FunctionalInterface
public interface PromiseRejecter {
    /**
     * 消费失败原因
     * @param reason Promise reject的原因
     * @return 新的值（可以是新的Promise或普通值）
     */
    Object accept(Object reason) throws Exception;
}
