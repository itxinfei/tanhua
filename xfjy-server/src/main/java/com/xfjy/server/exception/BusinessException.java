package com.xfjy.server.exception;

import lombok.Getter;

/**
 * 业务异常（Controller/Service 中显式抛出，携带错误码）
 */
@Getter
public class BusinessException extends RuntimeException {
    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);
    }
}
