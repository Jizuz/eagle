package com.eagle.common.util;

import com.eagle.common.exception.EagleException;
import com.eagle.common.result.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 配置值类型强校验器单元测试
 */
class ValueTypeValidatorTest {

    // ==================== string ====================

    @Test
    void stringAcceptsAnyValue() {
        assertDoesNotThrow(() -> ValueTypeValidator.checkAndGet("string", "支付服务"));
        assertDoesNotThrow(() -> ValueTypeValidator.checkAndGet("string", ""));
        assertDoesNotThrow(() -> ValueTypeValidator.checkAndGet("string", "hello world 123!@#"));
    }

    // ==================== number ====================

    @Test
    void numberAcceptsIntegerAndDecimal() {
        assertDoesNotThrow(() -> ValueTypeValidator.checkAndGet("number", "5000"));
        assertDoesNotThrow(() -> ValueTypeValidator.checkAndGet("number", "-1"));
        assertDoesNotThrow(() -> ValueTypeValidator.checkAndGet("number", "3.14"));
        assertDoesNotThrow(() -> ValueTypeValidator.checkAndGet("number", "0"));
        assertDoesNotThrow(() -> ValueTypeValidator.checkAndGet("number", "-0.5"));
    }

    @Test
    void numberRejectsInvalidValue() {
        assertTypeMismatch("number", "abc");
        assertTypeMismatch("number", "");
        assertTypeMismatch("number", null);
        assertTypeMismatch("number", "true");
        assertTypeMismatch("number", "1e5");
        assertTypeMismatch("number", ".5");
        assertTypeMismatch("number", "5.");
        assertTypeMismatch("number", "+5");
    }

    // ==================== boolean ====================

    @Test
    void booleanAcceptsOnlyLowercaseTrueFalse() {
        assertDoesNotThrow(() -> ValueTypeValidator.checkAndGet("boolean", "true"));
        assertDoesNotThrow(() -> ValueTypeValidator.checkAndGet("boolean", "false"));
    }

    @Test
    void booleanRejectsOtherValue() {
        assertTypeMismatch("boolean", "True");
        assertTypeMismatch("boolean", "TRUE");
        assertTypeMismatch("boolean", "1");
        assertTypeMismatch("boolean", "yes");
        assertTypeMismatch("boolean", "");
        assertTypeMismatch("boolean", null);
    }

    // ==================== 非法类型 ====================

    @Test
    void unknownTypeRejected() {
        EagleException e = assertThrows(EagleException.class,
                () -> ValueTypeValidator.checkAndGet("json", "{}"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), e.getCode());
    }

    private void assertTypeMismatch(String type, String value) {
        EagleException e = assertThrows(EagleException.class,
                () -> ValueTypeValidator.checkAndGet(type, value));
        assertEquals(ErrorCode.TYPE_MISMATCH.getCode(), e.getCode());
    }
}
