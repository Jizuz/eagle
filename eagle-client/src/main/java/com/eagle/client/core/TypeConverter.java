package com.eagle.client.core;

import com.eagle.common.dto.ConfigNodeDTO;
import com.eagle.common.exception.EagleException;
import com.eagle.common.result.ErrorCode;

import java.math.BigDecimal;

/**
 * 客户端类型转换器: 将ZK下发的{"type","value"}转换为业务声明的目标类型。
 * <p>按服务端存储类型强校验（文档 4.2）: boolean类型节点仅可转Boolean，
 * number类型节点可转各数字类型（整型目标要求值无小数部分），string类型节点仅可转String。</p>
 */
public final class TypeConverter {

    private TypeConverter() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T convert(String key, ConfigNodeDTO node, Class<T> targetType) {
        if (node == null) {
            throw new EagleException(ErrorCode.CONFIG_NOT_FOUND, "配置不存在: " + key);
        }
        String value = node.getValue();
        String type = node.getType();

        if (String.class == targetType) {
            return (T) value;
        }
        if (Boolean.class == targetType || Boolean.TYPE == targetType) {
            if (!"boolean".equals(type) || (!"true".equals(value) && !"false".equals(value))) {
                throw mismatch(key, type, value, "Boolean");
            }
            return (T) Boolean.valueOf(value);
        }
        if (Integer.class == targetType || Integer.TYPE == targetType) {
            return (T) parseInteger(key, type, value);
        }
        if (Long.class == targetType || Long.TYPE == targetType) {
            return (T) parseNumber(key, type, value, "Long", Long::valueOf);
        }
        if (Double.class == targetType || Double.TYPE == targetType) {
            return (T) parseNumber(key, type, value, "Double", Double::valueOf);
        }
        if (Float.class == targetType || Float.TYPE == targetType) {
            return (T) parseNumber(key, type, value, "Float", Float::valueOf);
        }
        if (Short.class == targetType || Short.TYPE == targetType) {
            return (T) parseNumber(key, type, value, "Short", Short::valueOf);
        }
        if (BigDecimal.class == targetType) {
            return (T) parseNumber(key, type, value, "BigDecimal", BigDecimal::new);
        }
        throw new EagleException(ErrorCode.PARAM_ERROR,
                "配置[" + key + "]不支持的目标类型: " + targetType.getName()
                        + "，支持 String/Boolean/Integer/Long/Double/Float/Short/BigDecimal");
    }

    private static Integer parseInteger(String key, String type, String value) {
        if (!"number".equals(type)) {
            throw mismatch(key, type, value, "Integer");
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw mismatch(key, type, value, "Integer");
        }
    }

    private static <N> N parseNumber(String key, String type, String value, String target,
                                     java.util.function.Function<String, N> parser) {
        if (!"number".equals(type)) {
            throw mismatch(key, type, value, target);
        }
        try {
            return parser.apply(value);
        } catch (NumberFormatException e) {
            throw mismatch(key, type, value, target);
        }
    }

    private static EagleException mismatch(String key, String serverType, String value, String targetType) {
        return new EagleException(ErrorCode.TYPE_MISMATCH,
                String.format("配置[%s]服务端类型为%s(值:%s)，无法转换为目标类型%s", key, serverType, value, targetType));
    }
}
