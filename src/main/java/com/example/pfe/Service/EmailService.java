package com.example.pfe.Service;

import com.example.pfe.enums.ProjectStatus;
import com.example.pfe.exception.BusinessException;
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

    // ========== CORRECTION DES MÉTHODES D'ACTIVATION ==========


    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName,
                                       String newPassword, String employeeCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("🔐 Your Password Reset");

            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("employeeCode", employeeCode);
            context.setVariable("newPassword", newPassword);

            String htmlContent = templateEngine.process("password-reset-email", context);

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Password reset email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Error sending password reset email: {}", e.getMessage());
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    // ========== MÉTHODES POUR LES PROJETS ==========

    @Async
    public void sendProjectAssignmentEmail(String toEmail, String fullName,
                                           String projectName, String projectDescription,
                                           String assignmentNotes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("📋 You've been assigned to manage: " + projectName);

            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("projectName", projectName);
            context.setVariable("projectDescription", projectDescription);
            context.setVariable("assignmentNotes", assignmentNotes);
            context.setVariable("assignmentDate", LocalDateTime.now());

            String htmlContent = templateEngine.process("project-assignment-email", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Project assignment email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Error sending project assignment email: {}", e.getMessage());
            throw new RuntimeException("Failed to send project assignment email", e);
        }
    }

    @Async
    public void sendProjectUnassignmentEmail(String toEmail, String recipientName,
                                             String projectName) {
        log.info("Sending unassignment email to {} for project {}", toEmail, projectName);
        log.info("Email sent: Project '{}' unassigned from {}", projectName, recipientName);

        // Optionnel: Implémentez l'envoi d'email réel ici
    }

    @Async
    public void sendProjectStatusUpdateEmail(String toEmail, String firstName,
                                             String projectName, ProjectStatus status) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("📊 Project Status Updated: " + projectName);

            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("projectName", projectName);
            context.setVariable("newStatus", status);
            context.setVariable("updateDate", LocalDateTime.now());

            String htmlContent = templateEngine.process("project-status-update-email", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Project status update email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Error sending project status update email: {}", e.getMessage());
            throw new RuntimeException("Failed to send project status update email", e);
        }
    }

    // ========== MÉTHODES DE DÉVELOPPEMENT ==========

    /**
     * Méthode de simulation pour le développement
     */
    public void sendWelcomeEmailDev(String toEmail, String fullName,
                                    String employeeCode, String defaultPassword,
                                    String activationToken) {
        log.info("=== SIMULATED EMAIL (DEV) ===");
        log.info("To: {}", toEmail);
        log.info("Full Name: {}", fullName);
        log.info("Employee Code: {}", employeeCode);
        log.info("Temporary Password: {}", defaultPassword);
        log.info("Activation Token: {}", activationToken);
        log.info("==============================");
    }

    /**
     * Méthode utilitaire pour tester l'envoi d'email
     */
    @Async
    public void sendTestEmail(String toEmail, String subject, String message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(message, false);

            mailSender.send(mimeMessage);
            log.info("Test email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send test email: {}", e.getMessage());
        }
    }



    @Async
    public void sendWelcomeEmail(String toEmail, String fullName,
                                 String employeeCode,
                                 String defaultPassword,
                                 String activationToken) {
        try {
            log.info("=== SIMULATED EMAIL - WELCOME ===");
            log.info("To: {}", toEmail);
            log.info("Full Name: {}", fullName);
            log.info("Employee Code: {}", employeeCode);
            log.info("Temporary Password: {}", defaultPassword);
            log.info("Activation Token: {}", activationToken);
            log.info("=================================");
        } catch (Exception e) {
            log.warn("Skipping real email sending: {}", e.getMessage());
        }
    }

    /**
     * Email de rappel d'activation
     * - 2 paramètres seulement
     */
    @Async
    public void sendActivationReminderEmail(String toEmail, String activationToken) {
        try {
            log.info("=== SIMULATED EMAIL - ACTIVATION REMINDER ===");
            log.info("To: {}", toEmail);
            log.info("Activation Token: {}", activationToken);
            log.info("============================================");
        } catch (Exception e) {
            log.error("Failed to send activation reminder email: {}", e.getMessage());
        }
    }

}