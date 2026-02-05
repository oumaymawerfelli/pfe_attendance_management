package com.example.pfe.Service;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

   // @Value("${app.frontend.url:http://localhost:3000}")
   // private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName,
                                 String employeeCode,
                                 String defaultPassword,
                                 String activationToken) {

        try {/*
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to Our Company - Your Account Credentials");

            // Activation link
            String activationLink = frontendUrl + "/activate-account?token=" + activationToken;

            // Prepare data for the template
            Context context = new Context();
            context.setVariable("fullName", fullName); //  ADD FULL NAME
            context.setVariable("employeeCode", employeeCode);
            context.setVariable("defaultPassword", defaultPassword);
            context.setVariable("activationLink", activationLink);
            context.setVariable("companyName", "Your Company Name");

            // Load the HTML template
            String htmlContent = templateEngine.process("welcome-email", context);

            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info("Welcome email sent to: {}", toEmail);
*/
            sendWelcomeEmailDev(toEmail, fullName, employeeCode, defaultPassword, activationToken);
      /*  } catch (MessagingException e) {
            log.error("Error sending email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }*/
        } catch (Exception e) {
            log.warn("Skipping real email sending (DEV mode): {}", e.getMessage());
        }
    }

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

    //  SIMPLIFIED METHOD FOR DEVELOPMENT
    public void sendWelcomeEmailDev(String toEmail, String fullName,
                                    String employeeCode, String defaultPassword,
                                    String activationToken) {
        log.info("=== SIMULATED EMAIL (DEV) ===");
        log.info("To: {}", toEmail);
        log.info("Full Name: {}", fullName);
        log.info("Employee Code: {}", employeeCode);
        log.info("Temporary Password: {}", defaultPassword);
        log.info("Activation Token: {}", activationToken);
       // log.info("Activation Link: {}/activate-account?token={}", frontendUrl, activationToken);
        log.info("==============================");
    }
//Reset password for user who lost their temporary password
//     * Only admin can do this
    public void sendActivationReminderEmail(String to, String fullName, String activationToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Reminder: Activate Your Account");
            helper.setFrom(fromEmail);

            String activationLink =  "/activate?token=" + activationToken;

            String htmlContent = String.format("""
            <html>
            <body>
                <h2>Account Activation Reminder</h2>
                <p>Hi %s,</p>
                <p>This is a reminder to activate your account.</p>
                <p>If you lost your temporary password, please contact your administrator 
                   to reset it.</p>
                <p>Click the link below to activate your account:</p>
                <p><a href="%s">Activate Account</a></p>
                <p>This link will expire soon.</p>
            </body>
            </html>
            """, fullName, activationLink);

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (MessagingException e) {
            throw new BusinessException("Failed to send activation reminder email");
        }
    }
}
