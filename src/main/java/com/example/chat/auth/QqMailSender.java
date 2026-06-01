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
            message.setSubject("Jakarta Chat verification code", "UTF-8");
            message.setText("Your verification code is: " + code + ". It expires in 10 minutes.", "UTF-8");
            Transport.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to send QQ email.", exception);
        }
    }
}
