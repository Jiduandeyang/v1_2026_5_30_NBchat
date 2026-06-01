package com.example.chat.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseConfigTest {
    @Test
    void hikariConfigUsesExplicitMysqlDriver() {
        assertEquals("com.mysql.cj.jdbc.Driver", Database.hikariConfig().getDriverClassName());
    }
}
