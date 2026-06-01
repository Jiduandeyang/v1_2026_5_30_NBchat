package com.example.chat.auth;

import com.example.chat.common.AppException;
import com.example.chat.common.Validation;
import com.example.chat.config.Database;

import java.security.SecureRandom;
import java.sql.Connection;
import java.time.LocalDateTime;

public class EmailCodeService {
    private final AuthDao authDao = new AuthDao();
    private final QqMailSender mailSender = new QqMailSender();
    private final SecureRandom random = new SecureRandom();

    public void send(String qqEmail, String purpose) {
        String normalizedEmail = Validation.normalizeQqEmail(qqEmail);
        String code = String.format("%06d", random.nextInt(1_000_000));
        try (Connection connection = Database.connection()) {
            long remainingSeconds = EmailCodeCooldown.remainingSeconds(
                    authDao.latestEmailCodeCreatedAt(connection, normalizedEmail, purpose),
                    LocalDateTime.now());
            if (remainingSeconds > 0) {
                throw AppException.badRequest("Please wait " + remainingSeconds + " seconds before requesting another verification code.");
            }
            authDao.markExistingCodesUsed(connection, normalizedEmail, purpose);
            authDao.markPreviousEmailCodesUsed(connection, normalizedEmail, purpose);
            authDao.saveEmailCode(connection, normalizedEmail, code, purpose);
            mailSender.sendCode(normalizedEmail, code, purpose);
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }
}
