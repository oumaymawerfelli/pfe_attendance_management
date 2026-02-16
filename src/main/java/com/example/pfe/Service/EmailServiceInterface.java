package com.example.pfe.Service;

public interface EmailServiceInterface {
    void sendWelcomeEmail(String toEmail, String fullName, String defaultPassword, String activationToken);
    void sendActivationReminderEmail(String toEmail, String activationToken);
    void sendPasswordResetEmail(String toEmail, String fullName, String newPassword);
    void sendProjectAssignmentEmail(String toEmail, String fullName, String projectName,
                                    String projectDescription, String assignmentNotes);
    void sendSimpleEmail(String toEmail, String subject, String body);
}