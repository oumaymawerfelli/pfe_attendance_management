package com.example.pfe.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;      // Sends actual emails
    private final TemplateEngine templateEngine;  // Creates HTML emails from templates

    @Value("${spring.mail.username}")
    private String fromEmail;  // Sender email address (from config)

    @Value("${app.frontend.url}")
    private String frontendUrl;  // Frontend URL for links (from config)

    @Value("${app.email.simulated:false}")
    private boolean simulated;  // If true, doesn't send real emails (for testing)

    //Converts the company logo to base64 format so it can be embedded directly in HTML emails(still dont work
    private String getLogoBase64() {
        try {
            ClassPathResource logo = new ClassPathResource("static/images/logo1.jpg");
            byte[] bytes = logo.getInputStream().readAllBytes();
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.warn("Logo not found: {}", e.getMessage());
            return "";
        }
    }
//After admin approves registration
    //Sends email with activation link and temporary password
    @Async//@Async means email sends in background (doesn't block user)
    public void sendWelcomeEmail(String toEmail, String fullName,
                                 String defaultPassword, String activationToken) {

        String activationLink = frontendUrl + "/#/auth/activate?token=" + activationToken;

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
            helper.setSubject("Bienvenue sur ArabSoft - Activez votre compte");

            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("toEmail", toEmail);
            context.setVariable("defaultPassword", defaultPassword);
            context.setVariable("activationToken", activationToken);
            context.setVariable("activationLink", activationLink);
            context.setVariable("currentYear", Year.now().getValue());
            context.setVariable("logoBase64", getLogoBase64());

            String htmlContent = templateEngine.process("welcome", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Welcome email sent successfully to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
            logFallback("WELCOME", toEmail, fullName, defaultPassword, activationToken);
        }
    }
// Admin can resend activation email if user lost it or didn't receive it
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
            helper.setSubject("Rappel : Activez votre compte ArabSoft");

            Context context = new Context();
            context.setVariable("toEmail", toEmail);
            context.setVariable("activationToken", activationToken);
            context.setVariable("activationLink", frontendUrl + "/#/auth/activate?token=" + activationToken);
            context.setVariable("currentYear", LocalDateTime.now().getYear());
            context.setVariable("logoBase64", getLogoBase64());

            String htmlContent = templateEngine.process("reminder", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Activation reminder sent to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send reminder: {}", e.getMessage());
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
            context.setVariable("loginLink", frontendUrl + "/#/login");
            context.setVariable("currentYear", Year.now().getValue());
            context.setVariable("logoBase64", getLogoBase64());

            String htmlContent = templateEngine.process("password-reset", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Password reset email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send password reset to {}: {}", toEmail, e.getMessage());
            log.info("FALLBACK - New password for {}: {}", toEmail, newPassword);
        }
    }
    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info(" Email sent to: {}", to);
        } catch (MessagingException e) {
            log.error(" Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    public void sendAccountDisabledEmail(String to, String fullName) {
        String subject = "Account Disabled";
        String body = String.format(
                "<h2>Hello %s,</h2>" +
                        "<p>Your account has been disabled by an administrator.</p>" +
                        "<p>If you believe this is an error, please contact your system administrator.</p>" +
                        "<br><p>Best regards,<br>HR Management Team</p>",
                fullName
        );
        sendEmail(to, subject, body);
    }

    public void sendAccountApprovedEmail(String to, String fullName) {
        String subject = "Account Approved";
        String body = String.format(
                "<h2>Welcome %s!</h2>" +
                        "<p>Your account has been approved. You can now log in to the system.</p>" +
                        "<p><a href='%s/login'>Click here to login</a></p>" +
                        "<br><p>Best regards,<br>HR Management Team</p>",
                fullName, frontendUrl
        );
        sendEmail(to, subject, body);
    }



    private void logSimulatedEmail(String type, String to, String details) {
        log.info(" === SIMULATED EMAIL - {} ===", type);
        log.info("To: {}", to);
        log.info("Details: {}", details);
        log.info(" ============================\n");
    }

    private void logFallback(String type, String toEmail, String fullName,
                             String defaultPassword, String activationToken) {
        log.info("\n === FALLBACK - {} ===", type);
        log.info("To: {}", toEmail);
        log.info("Full Name: {}", fullName);
        log.info("Password: {}", defaultPassword);
        log.info("Token: {}", activationToken);
        log.info("Link: {}/auth/activate?token={}", frontendUrl, activationToken);
        log.info("=====================\n");
    }
    //Notify users when they're added/removed from projects
   /* @Async
    public void sendProjectAssignmentEmail(String toEmail, String fullName,
                                           String projectName, String projectDescription,
                                           String assignmentNotes) {
        if (simulated) {
            log.info("\n === SIMULATED EMAIL - PROJECT ASSIGNMENT ===");
            log.info("To: {}", toEmail);
            log.info("Full Name: {}", fullName);
            log.info("Project: {}", projectName);
            log.info("Description: {}", projectDescription);
            log.info("Notes: {}", assignmentNotes);
            log.info(" ===========================================\n");
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
            context.setVariable("currentYear", Year.now().getValue());
            context.setVariable("logoBase64", getLogoBase64());

            String htmlContent = templateEngine.process("project-assignment-email", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Project assignment email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Error sending project assignment email: {}", e.getMessage());
            log.info("\n === FALLBACK - PROJECT ASSIGNMENT ===");
            log.info("To: {}", toEmail);
            log.info("Project: {}", projectName);
            log.info(" ====================================\n");
        }
    }

    @Async
    public void sendProjectUnassignmentEmail(String toEmail, String recipientName,
                                             String projectName) {
        if (simulated) {
            log.info("\n === SIMULATED EMAIL - PROJECT UNASSIGNMENT ===");
            log.info("To: {}", toEmail);
            log.info("Recipient: {}", recipientName);
            log.info("Project: {}", projectName);
            log.info("=============================================\n");
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
            context.setVariable("currentYear", Year.now().getValue());
            context.setVariable("logoBase64", getLogoBase64());

            String htmlContent = templateEngine.process("project-unassignment-email", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info(" Unassignment email sent to: {}", toEmail);

        } catch (MessagingException e) {
            log.error(" Error sending unassignment email: {}", e.getMessage());
            log.info("\n === FALLBACK - PROJECT UNASSIGNMENT ===");
            log.info("To: {}", toEmail);
            log.info("Project: {}", projectName);
            log.info(" ======================================\n");
        }
    }*/
    /*
    public void sendAccountRejectedEmail(String to, String fullName) {
        String subject = "Registration Update";
        String body = String.format(
                "<h2>Hello %s,</h2>" +
                        "<p>We regret to inform you that your registration request has been rejected.</p>" +
                        "<p>Please contact HR for more information.</p>" +
                        "<br><p>Best regards,<br>HR Management Team</p>",
                fullName
        );
        sendEmail(to, subject, body);
    }*/

}
