package com.timelordtty.dca.exception;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {
    /**
     * 执行 BusinessException 相关后端逻辑，保持既有业务契约不变。
     */
    public BusinessException(String message) {
        super(message);
    }

    /**
     * 执行 BusinessException 相关后端逻辑，保持既有业务契约不变。
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

