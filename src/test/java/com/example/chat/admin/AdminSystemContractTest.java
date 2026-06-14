package com.example.chat.admin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSystemContractTest {
    private static final Path ROOT = Path.of(".");

    @Test
    void schemaAndMigratorIncludeAdminState() throws IOException {
        String schema = read("src/main/resources/schema.sql");
        String migrator = read("src/main/java/com/example/chat/config/SchemaMigrator.java");

        assertTrue(schema.contains("disabled TINYINT(1) NOT NULL DEFAULT 0"));
        assertTrue(schema.contains("admin_audit_logs"));
        assertTrue(migrator.contains("ensureUsersDisabledColumn"));
        assertTrue(migrator.contains("ensureAdminAuditLogsTable"));
    }

    @Test
    void adminBackendIsProtectedAndExposesManagementApi() throws IOException {
        String session = read("src/main/java/com/example/chat/common/SessionSupport.java");
        String resource = read("src/main/java/com/example/chat/admin/AdminResource.java");
        String service = read("src/main/java/com/example/chat/admin/AdminService.java");
        String dao = read("src/main/java/com/example/chat/admin/AdminDao.java");

        assertTrue(session.contains("requireAdmin"));
        assertTrue(resource.contains("@Path(\"/admin\")"));
        assertTrue(resource.contains("/dashboard"));
        assertTrue(resource.contains("/users"));
        assertTrue(resource.contains("/groups"));
        assertTrue(resource.contains("/moments"));
        assertTrue(resource.contains("/audit-logs"));
        assertTrue(resource.contains("SessionSupport.requireAdmin"));
        assertTrue(service.contains("logAction"));
        assertTrue(dao.contains("admin_audit_logs"));
    }

    @Test
    void disabledUsersCannotLoginAndUserModelCarriesStatus() throws IOException {
        String user = read("src/main/java/com/example/chat/model/User.java");
        String authService = read("src/main/java/com/example/chat/auth/AuthService.java");
        String authDao = read("src/main/java/com/example/chat/auth/AuthDao.java");
        String userDao = read("src/main/java/com/example/chat/user/UserDao.java");

        assertTrue(user.contains("boolean disabled"));
        assertTrue(authService.contains("user.disabled()"));
        assertTrue(authDao.contains("disabled"));
        assertTrue(userDao.contains("disabled"));
    }

    @Test
    void adminFrontendIsWiredIntoDashboard() throws IOException {
        String html = read("src/main/webapp/app.html");
        String app = read("src/main/webapp/assets/js/app.js");
        String admin = read("src/main/webapp/assets/js/admin.js");
        String css = read("src/main/webapp/assets/css/dashboard.css");

        assertTrue(html.contains("id=\"adminNavButton\""));
        assertTrue(html.contains("id=\"adminView\""));
        assertTrue(html.contains("assets/js/admin.js"));
        assertTrue(app.contains("adminNavButton"));
        assertTrue(app.contains("AppState.me.role === \"ADMIN\""));
        assertTrue(admin.contains("loadAdminDashboard"));
        assertTrue(admin.contains("/admin/dashboard"));
        assertTrue(admin.contains("/admin/users"));
        assertTrue(admin.contains("/admin/groups"));
        assertTrue(admin.contains("/admin/moments"));
        assertTrue(admin.contains("/admin/audit-logs"));
        assertTrue(css.contains(".admin-panel"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
