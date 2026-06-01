package com.example.chat.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class DatabaseLifecycleListener implements ServletContextListener {
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        Database.close();
    }
}
