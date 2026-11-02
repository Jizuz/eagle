package com.eagle.common.util;

import com.eagle.common.enums.ValueType;
import com.eagle.common.exception.EagleException;
import com.eagle.common.result.ErrorCode;

import java.util.regex.Pattern;

/**
 * 配置值类型强校验器（后端强校验规则，见文档 4.2）:
 * <ol>
 *     <li>string: 无严格限制，允许空字符串</li>
 *     <li>number: 必须是合法数字，支持整数、小数（允许负号）</li>
 *     <li>boolean: 仅允许小写 true、false</li>
 * </ol>
 * 修改配置类型后发布时，同样使用本校验器强制校验值是否匹配新类型。
 */
public final class ValueTypeValidator {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private ValueTypeValidator() {
    }

    /**
     * 校验配置类型与值是否匹配，不匹配直接抛出业务异常
     *
     * @return 校验通过的类型枚举
     */
    public static ValueType checkAndGet(String valueType, String value) {
        ValueType type = ValueType.of(valueType);
        switch (type) {
            case STRING:
                return type;
            case NUMBER:
                if (value == null || value.isEmpty() || !NUMBER_PATTERN.matcher(value).matches()) {
                    throw new EagleException(ErrorCode.TYPE_MISMATCH,
                            "number 类型要求值为合法数字（整数或小数），当前值: [" + value + "]");
                }
                return type;
            case BOOLEAN:
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new EagleException(ErrorCode.TYPE_MISMATCH,
                            "boolean 类型仅允许小写 true / false，当前值: [" + value + "]");
                }
                return type;
            default:
                throw new EagleException(ErrorCode.PARAM_ERROR, "不支持的配置类型: " + valueType);
        }
    }
}
