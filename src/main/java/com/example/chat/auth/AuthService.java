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
        String normalizedEmail = Validation.normalizeQqEmail(qqEmail);
        try (Connection connection = Database.connection()) {
            if (authDao.emailExists(connection, normalizedEmail)) {
                throw AppException.badRequest("Email is already registered.");
            }
            emailCodeService.send(normalizedEmail, REGISTER);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public void sendResetCode(String qqEmail) {
        String normalizedEmail = Validation.normalizeQqEmail(qqEmail);
        try (Connection connection = Database.connection()) {
            if (!authDao.emailExists(connection, normalizedEmail)) {
                throw AppException.badRequest("Email is not registered.");
            }
            emailCodeService.send(normalizedEmail, RESET_PASSWORD);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public User register(RegisterRequest request) {
        RegisterRequest normalizedRequest = normalize(request);
        validateRegister(normalizedRequest);
        try (Connection connection = Database.connection()) {
            if (authDao.usernameExists(connection, normalizedRequest.username())) {
                throw AppException.badRequest("Username is already taken.");
            }
            if (authDao.emailExists(connection, normalizedRequest.qqEmail())) {
                throw AppException.badRequest("Email is already registered.");
            }
            if (!authDao.consumeEmailCode(connection, normalizedRequest.qqEmail(), normalizedRequest.code(), REGISTER)) {
                throw AppException.badRequest("Invalid or expired verification code.");
            }
            long id = authDao.createUser(connection, normalizedRequest, PasswordHasher.hash(normalizedRequest.password()));
            return authDao.findByUsername(connection, normalizedRequest.username());
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
            User user = authDao.findByUsername(connection, request.username());
            if (user != null && user.disabled()) {
                throw new AppException(Response.Status.FORBIDDEN, "Account is disabled.");
            }
            return user;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public void resetPassword(PasswordResetRequest request) {
        String normalizedEmail = Validation.normalizeQqEmail(request.qqEmail());
        Validation.require(request.newPassword() != null && request.newPassword().length() >= 6, "Password must contain at least 6 characters.");
        Validation.notBlank(request.code(), "Verification code is required.");
        try (Connection connection = Database.connection()) {
            if (!authDao.emailExists(connection, normalizedEmail)) {
                throw AppException.badRequest("Email is not registered.");
            }
            if (!authDao.consumeEmailCode(connection, normalizedEmail, request.code() == null ? "" : request.code().trim(), RESET_PASSWORD)) {
                throw AppException.badRequest("Invalid or expired verification code.");
            }
            authDao.updatePassword(connection, normalizedEmail, PasswordHasher.hash(request.newPassword()));
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

    private RegisterRequest normalize(RegisterRequest request) {
        return new RegisterRequest(
                request.username().trim(),
                request.password(),
                Validation.normalizeQqEmail(request.qqEmail()),
                request.code().trim(),
                request.nickname() == null ? null : request.nickname().trim()
        );
    }
}
