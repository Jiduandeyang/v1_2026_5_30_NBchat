package com.example.chat.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class Database {
    private Database() {
    }

    public static Connection connection() throws SQLException {
        return Holder.DATA_SOURCE.getConnection();
    }

    public static DataSource dataSource() {
        return Holder.DATA_SOURCE;
    }

    public static void close() {
        Holder.DATA_SOURCE.close();
    }

    private static HikariDataSource createDataSource() {
        HikariDataSource dataSource = new HikariDataSource(hikariConfig());
        SchemaMigrator.ensure(dataSource);
        SchemaMigrator.cleanupStaleVoiceCalls(dataSource);
        return dataSource;
    }

    static HikariConfig hikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(AppConfig.get("db.url", "jdbc:mysql://localhost:3306/jakarta_chat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(AppConfig.get("db.username", "root"));
        config.setPassword(AppConfig.get("db.password", ""));
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setPoolName("jakarta-chat-pool");
        return config;
    }

    private static final class Holder {
        private static final HikariDataSource DATA_SOURCE = createDataSource();
    }
}
