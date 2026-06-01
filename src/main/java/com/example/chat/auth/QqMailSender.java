package com.example.chat.auth;

import com.example.chat.config.AppConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class QqMailSender {
    public void sendCode(String qqEmail, String code, String purpose) {
        if (AppConfig.getBoolean("mail.devMode", true)) {
            System.out.printf("QQ mail dev code for %s [%s]: %s%n", qqEmail, purpose, code);
            return;
        }
        String username = AppConfig.get("qq.smtp.username", "");
        String authCode = AppConfig.get("qq.smtp.authCode", "");
        if (username.isBlank() || authCode.isBlank()) {
            throw new IllegalStateException("QQ mail is not configured. Set QQ_SMTP_USERNAME and QQ_SMTP_AUTHCODE.");
        }
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.host", "smtp.qq.com");
        properties.put("mail.smtp.port", "465");
        properties.put("mail.smtp.ssl.enable", "true");
        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, authCode);
            }
        });
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, qqEmail);
            message.setSubject("Jakarta Chat QQ 邮箱验证码", "UTF-8");
            message.setText(mailBody(code, purpose), "UTF-8");
            Transport.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to send QQ email.", exception);
        }
    }

    private String mailBody(String code, String purpose) {
        String action = "RESET_PASSWORD".equals(purpose) ? "找回密码" : "注册账号";
        return "你正在使用 Jakarta Chat " + action + "。\n\n" +
                "验证码：" + code + "\n" +
                "有效期：10 分钟。\n\n" +
                "如果不是你本人操作，请忽略这封邮件。";
    }
}
