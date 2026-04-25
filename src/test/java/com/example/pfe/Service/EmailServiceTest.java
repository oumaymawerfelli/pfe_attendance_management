package com.example.pfe.Service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

// ═══════════════════════════════════════════════════════════════════════════════
// POURQUOI TESTER EmailService DIFFÉREMMENT ?
//
// EmailService a deux comportements selon la config :
//   • simulated = true  → log seulement, pas de vrai email (mode test/dev)
//   • simulated = false → envoie un vrai email via JavaMailSender
//
// On ne teste PAS l'envoi réel d'email (ça dépend d'un serveur SMTP externe).
// On teste que :
//   ✅ En mode simulé → JavaMailSender n'est JAMAIS appelé
//   ✅ En mode réel   → JavaMailSender EST appelé avec les bons paramètres
//   ✅ Si l'envoi échoue → le service ne plante pas (gestion d'erreur)
//
// OUTIL SPÉCIAL utilisé ici :
//   ReflectionTestUtils.setField(objet, "nomDuChamp", valeur)
//   → Permet d'injecter des valeurs dans des champs @Value privés
//     (fromEmail, frontendUrl, simulated) sans passer par Spring Boot entier
// ═══════════════════════════════════════════════════════════════════════════════
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService — Tests Unitaires")
class EmailServiceTest {

    @Mock private JavaMailSender  mailSender;      // faux serveur email
    @Mock private TemplateEngine  templateEngine;  // faux moteur de templates HTML
    @Mock private MimeMessage     mimeMessage;     // faux objet message email

    @InjectMocks
    private EmailService emailService;

    // ─────────────────────────────────────────────────────────────────────────
    // @BeforeEach : injecte les valeurs @Value manquantes avant chaque test
    //
    // Normalement Spring Boot injecte ces valeurs depuis application.properties.
    // Mais dans un test unitaire, Spring Boot ne démarre pas.
    // ReflectionTestUtils.setField() remplace @Value directement.
    // ─────────────────────────────────────────────────────────────────────────
    @BeforeEach
    void setUp() {
        // Inject les champs @Value qui ne peuvent pas être injectés par Mockito
        ReflectionTestUtils.setField(emailService, "fromEmail",  "noreply@company.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:4200");
        // simulated = false par défaut → tests du vrai chemin d'envoi
        ReflectionTestUtils.setField(emailService, "simulated", false);
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 1 — sendWelcomeEmail()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("sendWelcomeEmail()")
    class SendWelcomeEmailTests {

        @Test
        @DisplayName("✅ Mode simulé — aucun email envoyé (JavaMailSender jamais appelé)")
        void sendWelcomeEmail_simulatedMode_doesNotSendEmail() {

            // GIVEN : on active le mode simulé
            ReflectionTestUtils.setField(emailService, "simulated", true);

            // WHEN : on appelle la méthode
            emailService.sendWelcomeEmail(
                    "jane@example.com", "Jane Doe",
                    "tempPass123", "activation-token-xyz");

            // THEN : JavaMailSender ne doit JAMAIS être appelé en mode simulé
            // never() = vérifier qu'une méthode n'a pas été appelée du tout
            then(mailSender).should(never()).createMimeMessage();
            then(mailSender).should(never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("✅ Mode réel — JavaMailSender est appelé pour créer et envoyer le message")
        void sendWelcomeEmail_realMode_callsMailSender() {

            // GIVEN : configurer le mock pour retourner un faux MimeMessage
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            // Simuler que le templateEngine retourne du HTML
            given(templateEngine.process(eq("welcome"), any(Context.class)))
                    .willReturn("<html>Welcome email content</html>");

            // WHEN
            emailService.sendWelcomeEmail(
                    "jane@example.com", "Jane Doe",
                    "tempPass123", "activation-token-xyz");

            // THEN : vérifier que le mail a bien été envoyé
            // times(1) = appelé exactement 1 fois (c'est la valeur par défaut de should())
            then(mailSender).should(times(1)).createMimeMessage();
            then(mailSender).should(times(1)).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("✅ Erreur SMTP — le service ne plante pas (exception silencieuse)")
        void sendWelcomeEmail_smtpError_doesNotThrow() {

            // GIVEN : simuler une erreur lors de la création du message
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            given(templateEngine.process(eq("welcome"), any(Context.class)))
                    .willReturn("<html>content</html>");
            // willThrow → simuler que send() lève une RuntimeException (ex: SMTP refusé)
            willThrow(new RuntimeException("SMTP connection refused"))
                    .given(mailSender).send(any(MimeMessage.class));

            // WHEN / THEN : aucune exception ne doit remonter vers l'appelant
            // assertThatNoException() = vérifie que le code s'exécute sans exception
            org.assertj.core.api.Assertions.assertThatNoException()
                    .isThrownBy(() -> emailService.sendWelcomeEmail(
                            "jane@example.com", "Jane Doe",
                            "tempPass123", "token"));
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 2 — sendActivationReminderEmail()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("sendActivationReminderEmail()")
    class SendActivationReminderTests {

        @Test
        @DisplayName("✅ Mode simulé — aucun email envoyé")
        void sendActivationReminderEmail_simulatedMode_doesNotSend() {

            // GIVEN
            ReflectionTestUtils.setField(emailService, "simulated", true);

            // WHEN
            emailService.sendActivationReminderEmail("jane@example.com", "new-token");

            // THEN
            then(mailSender).should(never()).createMimeMessage();
        }

        @Test
        @DisplayName("✅ Mode réel — email de rappel envoyé correctement")
        void sendActivationReminderEmail_realMode_sendsEmail() {

            // GIVEN
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            given(templateEngine.process(eq("reminder"), any(Context.class)))
                    .willReturn("<html>Reminder content</html>");

            // WHEN
            emailService.sendActivationReminderEmail("jane@example.com", "new-token");

            // THEN
            then(mailSender).should(times(1)).send(any(MimeMessage.class));
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 3 — sendPasswordResetEmail()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("sendPasswordResetEmail()")
    class SendPasswordResetTests {

        @Test
        @DisplayName("✅ Mode simulé — aucun email envoyé")
        void sendPasswordResetEmail_simulatedMode_doesNotSend() {

            // GIVEN
            ReflectionTestUtils.setField(emailService, "simulated", true);

            // WHEN
            emailService.sendPasswordResetEmail("jane@example.com", "Jane Doe", "newPass123");

            // THEN
            then(mailSender).should(never()).createMimeMessage();
        }

        @Test
        @DisplayName("✅ Mode réel — email de réinitialisation envoyé")
        void sendPasswordResetEmail_realMode_sendsEmail() {

            // GIVEN
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            given(templateEngine.process(eq("password-reset"), any(Context.class)))
                    .willReturn("<html>Reset content</html>");

            // WHEN
            emailService.sendPasswordResetEmail("jane@example.com", "Jane Doe", "newPass123");

            // THEN
            then(mailSender).should(times(1)).send(any(MimeMessage.class));
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 4 — sendAccountDisabledEmail() et sendAccountApprovedEmail()
    // Ces méthodes utilisent un helper privé sendEmail() simple (pas de template)
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("sendAccountDisabledEmail() et sendAccountApprovedEmail()")
    class SendAccountStatusEmailTests {

        @Test
        @DisplayName("✅ sendAccountDisabledEmail — email envoyé via JavaMailSender")
        void sendAccountDisabledEmail_sendsEmail() {

            // GIVEN : createMimeMessage() doit retourner un objet non-null
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            // WHEN
            emailService.sendAccountDisabledEmail("jane@example.com", "Jane Doe");

            // THEN
            then(mailSender).should(times(1)).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("✅ sendAccountApprovedEmail — email envoyé via JavaMailSender")
        void sendAccountApprovedEmail_sendsEmail() {

            // GIVEN
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            // WHEN
            emailService.sendAccountApprovedEmail("jane@example.com", "Jane Doe");

            // THEN
            then(mailSender).should(times(1)).send(any(MimeMessage.class));
        }
    }
}