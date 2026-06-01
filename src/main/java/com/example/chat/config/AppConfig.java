package com.example.chat.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class AppConfig {
    private static final Properties PROPERTIES = load();

    private AppConfig() {
    }

    public static String get(String key, String defaultValue) {
        String envKey = key.toUpperCase().replace('.', '_');
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return PROPERTIES.getProperty(key, defaultValue);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, Boolean.toString(defaultValue)));
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (input != null) {
                properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // Defaults and environment variables keep local development usable.
        }
        return properties;
    }
}
