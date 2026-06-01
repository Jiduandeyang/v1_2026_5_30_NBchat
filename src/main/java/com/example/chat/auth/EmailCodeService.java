package com.example.chat.auth;

import com.example.chat.common.AppException;
import com.example.chat.common.Validation;
import com.example.chat.config.Database;

import java.security.SecureRandom;
import java.sql.Connection;

public class EmailCodeService {
    private final AuthDao authDao = new AuthDao();
    private final QqMailSender mailSender = new QqMailSender();
    private final SecureRandom random = new SecureRandom();

    public void send(String qqEmail, String purpose) {
        Validation.qqEmail(qqEmail);
        String code = String.format("%06d", random.nextInt(1_000_000));
        try (Connection connection = Database.connection()) {
            authDao.saveEmailCode(connection, qqEmail, code, purpose);
            mailSender.sendCode(qqEmail, code, purpose);
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }
}
