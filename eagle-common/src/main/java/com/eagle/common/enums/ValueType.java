package com.eagle.common.enums;

import com.eagle.common.exception.EagleException;
import com.eagle.common.result.ErrorCode;

/**
 * 配置值类型
 */
public enum ValueType {

    /** 字符串: 无严格限制，允许空字符串 */
    STRING("string"),

    /** 数字: 必须是合法数字，支持整数、小数 */
    NUMBER("number"),

    /** 布尔: 仅允许小写 true / false */
    BOOLEAN("boolean");

    private final String code;

    ValueType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 根据类型码解析枚举，非法类型直接抛出业务异常
     */
    public static ValueType of(String code) {
        for (ValueType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new EagleException(ErrorCode.PARAM_ERROR, "配置类型非法: [" + code + "]，仅支持 string / number / boolean");
    }
}
