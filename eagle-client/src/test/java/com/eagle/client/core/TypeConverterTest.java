package com.eagle.client.core;

import com.eagle.common.dto.ConfigNodeDTO;
import com.eagle.common.exception.EagleException;
import com.eagle.common.result.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 客户端类型转换器单元测试
 */
class TypeConverterTest {

    @Test
    void convertToString() {
        assertEquals("支付服务", TypeConverter.convert("serviceName",
                new ConfigNodeDTO("string", "支付服务"), String.class));
        // number/boolean节点按原始字符串读出
        assertEquals("5000", TypeConverter.convert("timeout",
                new ConfigNodeDTO("number", "5000"), String.class));
    }

    @Test
    void convertToBoolean() {
        assertEquals(Boolean.TRUE, TypeConverter.convert("switch",
                new ConfigNodeDTO("boolean", "true"), Boolean.class));
        assertEquals(Boolean.FALSE, TypeConverter.convert("switch",
                new ConfigNodeDTO("boolean", "false"), Boolean.class));
        // 基本类型
        boolean primitive = TypeConverter.convert("switch",
                new ConfigNodeDTO("boolean", "true"), boolean.class);
        assertEquals(true, primitive);
    }

    @Test
    void convertToInteger() {
        assertEquals(5000, TypeConverter.convert("timeout",
                new ConfigNodeDTO("number", "5000"), Integer.class));
        assertEquals(-1, TypeConverter.convert("retry",
                new ConfigNodeDTO("number", "-1"), Integer.class));
    }

    @Test
    void convertToLongDoubleBigDecimal() {
        assertEquals(9999999999L, TypeConverter.convert("big",
                new ConfigNodeDTO("number", "9999999999"), Long.class));
        assertEquals(3.14, TypeConverter.convert("ratio",
                new ConfigNodeDTO("number", "3.14"), Double.class));
        assertEquals(0.5f, TypeConverter.convert("ratio",
                new ConfigNodeDTO("number", "0.5"), Float.class));
        assertEquals(new BigDecimal("3.14"), TypeConverter.convert("ratio",
                new ConfigNodeDTO("number", "3.14"), BigDecimal.class));
    }

    @Test
    void nullNodeThrowsConfigNotFound() {
        EagleException e = assertThrows(EagleException.class,
                () -> TypeConverter.convert("missing", null, String.class));
        assertEquals(ErrorCode.CONFIG_NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    void booleanTargetRequiresBooleanTypeNode() {
        EagleException e = assertThrows(EagleException.class,
                () -> TypeConverter.convert("switch", new ConfigNodeDTO("number", "1"), Boolean.class));
        assertEquals(ErrorCode.TYPE_MISMATCH.getCode(), e.getCode());
    }

    @Test
    void integerTargetRejectsDecimalValue() {
        EagleException e = assertThrows(EagleException.class,
                () -> TypeConverter.convert("timeout", new ConfigNodeDTO("number", "5000.5"), Integer.class));
        assertEquals(ErrorCode.TYPE_MISMATCH.getCode(), e.getCode());
    }

    @Test
    void numberTargetRequiresNumberTypeNode() {
        EagleException e = assertThrows(EagleException.class,
                () -> TypeConverter.convert("timeout", new ConfigNodeDTO("string", "5000"), Integer.class));
        assertEquals(ErrorCode.TYPE_MISMATCH.getCode(), e.getCode());
    }

    @Test
    void unsupportedTargetTypeRejected() {
        EagleException e = assertThrows(EagleException.class,
                () -> TypeConverter.convert("x", new ConfigNodeDTO("string", "v"), Object.class));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), e.getCode());
    }
}
