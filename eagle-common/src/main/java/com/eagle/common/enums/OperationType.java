package com.eagle.common.enums;

/**
 * 版本快照操作类型
 */
public enum OperationType {

    /** 新增 / 编辑 / 回滚 */
    UPDATE("UPDATE"),

    /** 删除 */
    DELETE("DELETE");

    private final String code;

    OperationType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 根据操作码解析枚举，未匹配返回 null
     */
    public static OperationType of(String code) {
        for (OperationType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
