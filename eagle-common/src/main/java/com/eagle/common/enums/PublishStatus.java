package com.eagle.common.enums;

/**
 * 发布记录状态: 0待发布 1成功 2失败
 */
public enum PublishStatus {

    PENDING(0, "待发布"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败");

    private final int code;
    private final String desc;

    PublishStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据状态码解析枚举，未匹配返回 null
     */
    public static PublishStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (PublishStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
