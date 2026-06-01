package com.example.chat.common;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class Jdbc {
    private Jdbc() {
    }

    public static long insert(Connection connection, String sql, SqlConsumer<PreparedStatement> binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.accept(statement);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No generated key returned.");
    }

    public static int update(Connection connection, String sql, SqlConsumer<PreparedStatement> binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(statement);
            return statement.executeUpdate();
        }
    }

    public static <T> List<T> list(Connection connection, String sql, SqlConsumer<PreparedStatement> binder, RowMapper<T> mapper) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapper.map(resultSet));
                }
                return rows;
            }
        }
    }

    public static <T> T one(Connection connection, String sql, SqlConsumer<PreparedStatement> binder, RowMapper<T> mapper) throws SQLException {
        List<T> rows = list(connection, sql, binder, mapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public interface RowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    public interface SqlConsumer<T> {
        void accept(T value) throws SQLException;
    }
}
