package com.eagle.common.result;

/**
 * 统一错误码
 */
public enum ErrorCode {

    SUCCESS(0, "success"),

    /** 通用参数错误 */
    PARAM_ERROR(400, "参数错误"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),

    /** 同环境+分组下已存在该配置键 */
    CONFIG_EXISTS(1001, "同环境+分组下已存在该配置键"),

    /** 配置不存在 */
    CONFIG_NOT_FOUND(1002, "配置不存在"),

    /** 配置值与配置类型不匹配 */
    TYPE_MISMATCH(1003, "配置值与配置类型不匹配"),

    /** 当前状态不允许该操作 */
    STATE_ERROR(1004, "当前状态不允许该操作"),

    /** 同步ZooKeeper失败 */
    ZK_SYNC_FAILED(2001, "配置同步ZooKeeper失败"),

    /** 系统内部错误 */
    SYSTEM_ERROR(500, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
