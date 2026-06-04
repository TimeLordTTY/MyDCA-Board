package com.timelordtty.dca.exception;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {
    /**
     * 创建业务异常并携带可展示错误信息，用于服务层主动阻断非法操作。
     */
    public BusinessException(String message) {
        super(message);
    }

    /**
     * 创建业务异常并携带可展示错误信息，用于服务层主动阻断非法操作。
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

