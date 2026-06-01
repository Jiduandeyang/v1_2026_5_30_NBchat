package com.example.chat.common;

import com.example.chat.config.Database;

import java.sql.Connection;

public final class Transactional {
    private Transactional() {
    }

    public static <T> T withConnection(SqlWork<T> work) {
        try (Connection connection = Database.connection()) {
            return work.run(connection);
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest(exception.getMessage());
        }
    }

    public static void run(SqlConsumer work) {
        withConnection(connection -> {
            work.run(connection);
            return null;
        });
    }

    public interface SqlWork<T> {
        T run(Connection connection) throws Exception;
    }

    public interface SqlConsumer {
        void run(Connection connection) throws Exception;
    }
}
