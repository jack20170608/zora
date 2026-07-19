package top.ilovemyhome.zora.poc.async.promise;

/**
 * Promise成功结果消费者接口
 * 对应JavaScript Promise中onFulfilled函数
 */
@FunctionalInterface
public interface PromiseConsumer extends PromiseCallback {
    /**
     * 消费成功结果
     * @param value Promise resolve的值
     * @return 新的值（可以是新的Promise或普通值）
     */
    @Override
    Object invoke(Object value) throws Exception;
}