package com.lnf.server.common;

import lombok.Getter;

/**
 * 业务异常：由全局异常处理器统一转换为 Result
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        this(1000, message);
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
