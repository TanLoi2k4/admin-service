package com.tlcn.admin_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.Authenticator;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * EmailService - Handles all email sending operations for admin-service
 * Responsibilities:
 * - SMTP configuration
 * - Email composition
 * - Email sending with error handling
 */
@Slf4j
@Service
public class EmailService {

    @Value("${email.username}")
    private String emailUsername;

    @Value("${email.password}")
    private String emailPassword;

    private Session mailSession;

    /**
     * Initialize SMTP session on application startup
     */
    @PostConstruct
    public void initializeMailSession() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        mailSession = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(emailUsername, emailPassword);
            }
        });

        log.info("Mail session initialized successfully");
    }

    /**
     * Send a simple email
     *
     * @param to       Recipient email address
     * @param subject  Email subject
     * @param content  Email body content
     */
    public void sendEmail(String to, String subject, String content) {
        try {
            MimeMessage message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(emailUsername));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(content);

            Transport.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new EmailSendingException("Email sending failed: " + e.getMessage(), e);
        }
    }

    /**
     * Send admin account creation notification
     *
     * @param to          Recipient email address
     * @param adminName   Admin full name
     * @param username    Admin username
     */
    public void sendAdminAccountCreated(String to, String adminName, String username) {
        String subject = "Admin Account Created";
        String content = String.format(
            "Dear %s,\n\n" +
            "Your admin account has been created successfully.\n\n" +
            "Username: %s\n" +
            "Email: %s\n\n" +
            "You can now login to the admin panel.\n\n" +
            "Best regards,\n" +
            "System Administrator",
            adminName, username, to
        );
        sendEmail(to, subject, content);
    }

    /**
     * Send admin account status change notification
     *
     * @param to          Recipient email address
     * @param adminName   Admin full name
     * @param newStatus   New account status
     */
    public void sendAdminStatusChanged(String to, String adminName, String newStatus) {
        String subject = "Admin Account Status Changed";
        String content = String.format(
            "Dear %s,\n\n" +
            "Your admin account status has been changed to: %s\n\n" +
            "If you have any questions, please contact system administrator.\n\n" +
            "Best regards,\n" +
            "System Administrator",
            adminName, newStatus
        );
        sendEmail(to, subject, content);
    }

    /**
     * Send password reset email with link
     *
     * @param to          Recipient email address
     * @param adminName   Admin full name
     * @param resetToken  Password reset token
     */
    public void sendPasswordResetEmail(String to, String adminName, String resetToken) {
        String subject = "Password Reset Request";
        String resetLink = "http://localhost:5173/reset-password?token=" + resetToken;
        String content = String.format(
            "Dear %s,\n\n" +
            "You have requested to reset your password.\n\n" +
            "Please click the link below to reset your password:\n" +
            "%s\n\n" +
            "This link will expire in 15 minutes.\n" +
            "If you did not request this password reset, please ignore this email.\n\n" +
            "Best regards,\n" +
            "System Administrator",
            adminName, resetLink
        );
        sendEmail(to, subject, content);
    }

    /**
     * Send password changed notification
     *
     * @param to          Recipient email address
     * @param adminName   Admin full name
     */
    public void sendPasswordChangedNotification(String to, String adminName) {
        String subject = "Password Changed Successfully";
        String content = String.format(
            "Dear %s,\n\n" +
            "Your password has been changed successfully.\n\n" +
            "If you did not make this change, please contact system administrator immediately.\n\n" +
            "Best regards,\n" +
            "System Administrator",
            adminName
        );
        sendEmail(to, subject, content);
    }

    /**
     * Custom exception for email sending failures
     */
    public static class EmailSendingException extends RuntimeException {
        public EmailSendingException(String message) {
            super(message);
        }

        public EmailSendingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}