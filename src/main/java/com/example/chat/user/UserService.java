package com.example.chat.user;

import com.example.chat.common.AppException;
import com.example.chat.config.Database;
import com.example.chat.model.User;

import java.sql.Connection;
import java.util.List;

public class UserService {
    private final UserDao userDao = new UserDao();

    public User get(long id) {
        try (Connection connection = Database.connection()) {
            User user = userDao.findById(connection, id);
            if (user == null) {
                throw AppException.badRequest("User not found.");
            }
            return user;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public List<User> search(String q) {
        try (Connection connection = Database.connection()) {
            return userDao.search(connection, q == null ? "" : q.trim());
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public User update(long id, ProfileUpdateRequest request) {
        try (Connection connection = Database.connection()) {
            userDao.updateProfile(connection, id, request);
            return userDao.findById(connection, id);
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }
}
