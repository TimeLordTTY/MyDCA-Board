package com.timelordtty.dca.exception;

/**
 * 业务异常
 */
/**
 * 业务注释规范化: BusinessException 后端组件，服务于 MyDCA-Board 财富中枢业务流程。
 *
 * <p>不改变原有接口、数据库结构、账本入账规则或持仓成本逻辑。</p>
 */
public class BusinessException extends RuntimeException {
    /**
     * 业务注释规范化: 处理 BusinessException 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param message message 业务字段，承载该对象在后端流程中的核心属性。
     */
    public BusinessException(String message) {
        super(message);
    }

    /**
     * 业务注释规范化: 处理 BusinessException 相关业务，保持现有接口路径、请求和响应字段不变。
     *
     * <p>该方法属于对外或可继承调用边界，调用方应遵循既有权限、账本和数据一致性约束。</p>
     * @param message message 业务字段，承载该对象在后端流程中的核心属性。
     * @param cause cause 业务字段，承载该对象在后端流程中的核心属性。
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

