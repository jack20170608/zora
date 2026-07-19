package top.ilovemyhome.zora.poc.async.promise;

/**
 * Promise解决函数接口
 * 对应JavaScript Promise中的resolve函数
 */
@FunctionalInterface
public interface PromiseResolver {
    /**
     * 解决Promise（标记为成功）
     * @param value 成功解决的值
     */
    void resolve(Object value);
}