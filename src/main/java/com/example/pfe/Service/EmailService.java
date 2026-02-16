package com.example.pfe.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.email.simulated:false}")
    private boolean simulated;

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName,
                                 String defaultPassword, String activationToken) {
        if (simulated) {
            logSimulatedEmail("WELCOME", toEmail,
                    String.format("FullName: %s, Password: %s, Token: %s",
                            fullName, defaultPassword, activationToken));
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Bienvenue sur l'application PFE");

            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("toEmail", toEmail);
            context.setVariable("defaultPassword", defaultPassword);
            context.setVariable("activationToken", activationToken);
            context.setVariable("activationLink", frontendUrl + "/auth/activate?token=" + activationToken);

            String htmlContent = templateEngine.process("welcome", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Welcome email sent successfully to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send welcome email to {}: {}", toEmail, e.getMessage());
            // Fallback: log the information
            logFallback("WELCOME", toEmail, fullName, defaultPassword, activationToken);
        }
    }

    @Async
    public void sendActivationReminderEmail(String toEmail, String activationToken) {
        if (simulated) {
            logSimulatedEmail("ACTIVATION REMINDER", toEmail, "Token: " + activationToken);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Rappel : Activez votre compte PFE");

            Context context = new Context();
            context.setVariable("toEmail", toEmail);
            context.setVariable("activationToken", activationToken);
            context.setVariable("activationLink", frontendUrl + "/auth/activate?token=" + activationToken);

            String htmlContent = templateEngine.process("reminder", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Activation reminder sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send reminder to {}: {}", toEmail, e.getMessage());
            log.info("🔷 FALLBACK - Reminder for {} with token: {}", toEmail, activationToken);
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String newPassword) {
        if (simulated) {
            logSimulatedEmail("PASSWORD RESET", toEmail,
                    String.format("FullName: %s, New Password: %s", fullName, newPassword));
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Réinitialisation de votre mot de passe");

            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("newPassword", newPassword);
            context.setVariable("loginLink", frontendUrl + "/login");

            String htmlContent = templateEngine.process("password-reset", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Password reset email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send password reset to {}: {}", toEmail, e.getMessage());
            log.info("🔷 FALLBACK - New password for {}: {}", toEmail, newPassword);
        }
    }

    @Async
    public void sendSimpleEmail(String toEmail, String subject, String body) {
        if (simulated) {
            logSimulatedEmail("SIMPLE", toEmail, String.format("Subject: %s, Body: %s", subject, body));
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, false);

            mailSender.send(message);
            log.info("✅ Simple email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send simple email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ========== MÉTHODES PRIVÉES ==========

    private void logSimulatedEmail(String type, String to, String details) {
        log.info("\n🔷 === SIMULATED EMAIL - {} ===", type);
        log.info("To: {}", to);
        log.info("Details: {}", details);
        log.info("🔷 ============================\n");
    }

    private void logFallback(String type, String toEmail, String fullName,
                             String defaultPassword, String activationToken) {
        log.info("\n🔷 === FALLBACK - {} ===", type);
        log.info("To: {}", toEmail);
        log.info("Full Name: {}", fullName);
        log.info("Password: {}", defaultPassword);
        log.info("Token: {}", activationToken);
        log.info("Link: {}/auth/activate?token={}", frontendUrl, activationToken);
        log.info("🔷 =====================\n");
    }
    @Async
    public void sendProjectAssignmentEmail(String toEmail, String fullName,
                                           String projectName, String projectDescription,
                                           String assignmentNotes) {
        if (simulated) {
            log.info("\n🔷 === SIMULATED EMAIL - PROJECT ASSIGNMENT ===");
            log.info("To: {}", toEmail);
            log.info("Full Name: {}", fullName);
            log.info("Project: {}", projectName);
            log.info("Description: {}", projectDescription);
            log.info("Notes: {}", assignmentNotes);
            log.info("🔷 ===========================================\n");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Vous avez été assigné au projet : " + projectName);

            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("projectName", projectName);
            context.setVariable("projectDescription", projectDescription);
            context.setVariable("assignmentNotes", assignmentNotes);
            context.setVariable("assignmentDate", LocalDateTime.now());

            String htmlContent = templateEngine.process("project-assignment-email", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Project assignment email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Error sending project assignment email: {}", e.getMessage());
            // Fallback
            log.info("\n🔷 === FALLBACK - PROJECT ASSIGNMENT ===");
            log.info("To: {}", toEmail);
            log.info("Project: {}", projectName);
            log.info("🔷 ====================================\n");
        }
    }
    @Async
    public void sendProjectUnassignmentEmail(String toEmail, String recipientName,
                                             String projectName) {
        if (simulated) {
            log.info("\n🔷 === SIMULATED EMAIL - PROJECT UNASSIGNMENT ===");
            log.info("To: {}", toEmail);
            log.info("Recipient: {}", recipientName);
            log.info("Project: {}", projectName);
            log.info("🔷 =============================================\n");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Désaffectation du projet : " + projectName);

            Context context = new Context();
            context.setVariable("recipientName", recipientName);
            context.setVariable("projectName", projectName);
            context.setVariable("unassignmentDate", LocalDateTime.now());

            String htmlContent = templateEngine.process("project-unassignment-email", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Unassignment email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("❌ Error sending unassignment email: {}", e.getMessage());
            // Fallback
            log.info("\n🔷 === FALLBACK - PROJECT UNASSIGNMENT ===");
            log.info("To: {}", toEmail);
            log.info("Project: {}", projectName);
            log.info("🔷 ======================================\n");
        }
    }
}