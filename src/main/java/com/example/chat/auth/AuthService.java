package com.example.chat.auth;

import com.example.chat.common.AppException;
import com.example.chat.common.PasswordHasher;
import com.example.chat.common.Validation;
import com.example.chat.config.Database;
import com.example.chat.model.User;
import jakarta.ws.rs.core.Response;

import java.sql.Connection;

public class AuthService {
    private static final String REGISTER = "REGISTER";
    private static final String RESET_PASSWORD = "RESET_PASSWORD";

    private final AuthDao authDao = new AuthDao();
    private final EmailCodeService emailCodeService = new EmailCodeService();

    public void sendRegisterCode(String qqEmail) {
        emailCodeService.send(qqEmail, REGISTER);
    }

    public void sendResetCode(String qqEmail) {
        emailCodeService.send(qqEmail, RESET_PASSWORD);
    }

    public User register(RegisterRequest request) {
        validateRegister(request);
        try (Connection connection = Database.connection()) {
            if (!authDao.consumeEmailCode(connection, request.qqEmail(), request.code(), REGISTER)) {
                throw AppException.badRequest("Invalid or expired verification code.");
            }
            long id = authDao.createUser(connection, request, PasswordHasher.hash(request.password()));
            return authDao.findByUsername(connection, request.username());
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public User login(LoginRequest request) {
        Validation.notBlank(request.username(), "Username is required.");
        Validation.notBlank(request.password(), "Password is required.");
        try (Connection connection = Database.connection()) {
            String hash = authDao.passwordHashByUsername(connection, request.username());
            if (!PasswordHasher.verify(request.password(), hash)) {
                throw new AppException(Response.Status.UNAUTHORIZED, "Wrong username or password.");
            }
            return authDao.findByUsername(connection, request.username());
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public void resetPassword(PasswordResetRequest request) {
        Validation.qqEmail(request.qqEmail());
        Validation.require(request.newPassword() != null && request.newPassword().length() >= 6, "Password must contain at least 6 characters.");
        try (Connection connection = Database.connection()) {
            if (!authDao.consumeEmailCode(connection, request.qqEmail(), request.code(), RESET_PASSWORD)) {
                throw AppException.badRequest("Invalid or expired verification code.");
            }
            authDao.updatePassword(connection, request.qqEmail(), PasswordHasher.hash(request.newPassword()));
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    private void validateRegister(RegisterRequest request) {
        Validation.require(request.username() != null && request.username().length() >= 3, "Username must contain at least 3 characters.");
        Validation.require(request.password() != null && request.password().length() >= 6, "Password must contain at least 6 characters.");
        Validation.qqEmail(request.qqEmail());
        Validation.notBlank(request.code(), "Verification code is required.");
    }
}
