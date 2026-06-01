package com.example.chat.auth;

import com.example.chat.common.AppException;
import com.example.chat.common.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRegistrationPolicyTest {
    @Test
    void qqEmailMustBeNumericQqMailboxOnly() {
        assertDoesNotThrow(() -> Validation.qqEmail("123456789@qq.com"));
        assertDoesNotThrow(() -> Validation.qqEmail("123456789@QQ.COM"));

        assertThrows(AppException.class, () -> Validation.qqEmail("alice@qq.com"));
        assertThrows(AppException.class, () -> Validation.qqEmail("123456789@foxmail.com"));
        assertThrows(AppException.class, () -> Validation.qqEmail("123456789@qq.com.evil"));
        assertThrows(AppException.class, () -> Validation.qqEmail("1234@qq.com"));
    }
}
