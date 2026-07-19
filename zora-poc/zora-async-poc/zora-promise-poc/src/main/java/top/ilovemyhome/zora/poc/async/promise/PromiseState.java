package top.ilovemyhome.zora.poc.async.promise;

/**
 * Promise状态枚举
 * 对应JavaScript Promise的三种状态
 */
public enum PromiseState {
    /** 初始状态，既不是fulfilled也不是rejected */
    PENDING,
    /** 操作成功完成 */
    FULFILLED,
    /** 操作失败 */
    REJECTED
}
