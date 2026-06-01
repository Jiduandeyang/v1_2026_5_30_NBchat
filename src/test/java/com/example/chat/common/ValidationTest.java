package com.example.chat.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationTest {
    @Test
    void qqEmailOnlyAcceptsNumericQqMailbox() {
        assertDoesNotThrow(() -> Validation.qqEmail("123456@qq.com"));
        assertDoesNotThrow(() -> Validation.qqEmail("100001@qq.com"));

        assertThrows(AppException.class, () -> Validation.qqEmail("abc@qq.com"));
        assertThrows(AppException.class, () -> Validation.qqEmail("123456@foxmail.com"));
        assertThrows(AppException.class, () -> Validation.qqEmail("123456@gmail.com"));
        assertThrows(AppException.class, () -> Validation.qqEmail("123@qq.com"));
    }

    @Test
    void normalizeQqEmailTrimsAndLowercases() {
        assertEquals("123456@qq.com", Validation.normalizeQqEmail(" 123456@QQ.COM "));
    }
}
