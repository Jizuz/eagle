package com.eagle.common.exception;

import com.eagle.common.result.ErrorCode;

/**
 * Eagle配置中心业务异常
 */
public class EagleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public EagleException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public EagleException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.code = errorCode.getCode();
    }

    public EagleException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
