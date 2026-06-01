package com.example.chat.config;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class SchemaMigrator {
    private SchemaMigrator() {
    }

    static void ensure(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            ensureMessagesReplyColumn(connection);
            ensureMessagesRecallColumn(connection);
            ensureMessageReactionsTable(connection);
            ensureConversationReadsTable(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to migrate schema.", exception);
        }
    }

    private static void ensureMessagesRecallColumn(Connection connection) throws SQLException {
        if (hasColumn(connection, "messages", "recalled_at")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE messages ADD COLUMN recalled_at TIMESTAMP NULL");
        }
    }

    private static void ensureMessagesReplyColumn(Connection connection) throws SQLException {
        if (hasColumn(connection, "messages", "reply_to_message_id")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE messages ADD COLUMN reply_to_message_id BIGINT NULL");
        }
    }

    private static void ensureMessageReactionsTable(Connection connection) throws SQLException {
        if (hasTable(connection, "message_reactions")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE message_reactions (
                        message_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        emoji VARCHAR(16) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (message_id, user_id, emoji),
                        FOREIGN KEY (message_id) REFERENCES messages(id),
                        FOREIGN KEY (user_id) REFERENCES users(id)
                    )
                    """);
        }
    }

    private static void ensureConversationReadsTable(Connection connection) throws SQLException {
        if (hasTable(connection, "conversation_reads")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE conversation_reads (
                        conversation_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        last_read_message_id BIGINT NOT NULL DEFAULT 0,
                        read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (conversation_id, user_id),
                        FOREIGN KEY (conversation_id) REFERENCES conversations(id),
                        FOREIGN KEY (user_id) REFERENCES users(id)
                    )
                    """);
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, table, column)) {
            return resultSet.next();
        }
    }

    private static boolean hasTable(Connection connection, String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }
}
