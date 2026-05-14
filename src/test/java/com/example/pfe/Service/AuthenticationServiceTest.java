package com.example.pfe.Service;

// ═══════════════════════════════════════════════════════════════════════════════
// IMPORTS — Les outils dont on a besoin pour écrire les tests
// ═══════════════════════════════════════════════════════════════════════════════
import com.example.pfe.Repository.RoleRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.*;
import com.example.pfe.entities.Role;
import com.example.pfe.entities.User;
import com.example.pfe.enums.RoleName;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.UserMapper;

// JUnit 5 — le framework de test
import org.junit.jupiter.api.BeforeEach;          // s'exécute AVANT chaque test
import org.junit.jupiter.api.DisplayName;          // nom lisible dans le rapport
import org.junit.jupiter.api.Nested;               // groupe les tests par méthode
import org.junit.jupiter.api.Test;                 // marque une méthode comme test
import org.junit.jupiter.api.extension.ExtendWith; // connecte JUnit 5 avec Mockito

// Mockito — crée de faux objets (mocks) à la place des vrais
import org.mockito.InjectMocks;                              // crée le vrai objet à tester
import org.mockito.Mock;                                     // crée un faux objet
import org.mockito.junit.jupiter.MockitoExtension;           // active Mockito dans JUnit 5

import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// AssertJ — pour écrire des vérifications lisibles
import static org.assertj.core.api.Assertions.*;

// Mockito méthodes statiques — configurer et vérifier les mocks
import static org.mockito.ArgumentMatchers.*; // any(), anyString(), eq(...)
import static org.mockito.BDDMockito.*;       // given(), then(), willReturn()


// ═══════════════════════════════════════════════════════════════════════════════
// @ExtendWith(MockitoExtension.class)
//   → Active Mockito pour cette classe. Sans ça, @Mock et @InjectMocks
//     ne fonctionnent pas.
//
// @DisplayName(...)
//   → Nom affiché dans le rapport de tests. Purement pour la lisibilité.
// ═══════════════════════════════════════════════════════════════════════════════
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationService — Tests Unitaires")
class AuthenticationServiceTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // LES MOCKS
    //
    // Un MOCK = un faux objet qui SIMULE le comportement d'une vraie classe.
    //
    // POURQUOI ?
    //   On veut tester UNIQUEMENT AuthenticationService, pas la base de données,
    //   pas le serveur email, etc.
    //   Donc on remplace UserRepository, EmailService... par des faux objets
    //   qu'on programme pour retourner ce qu'on veut.
    //
    // EXEMPLE :
    //   Au lieu d'aller en base de données, on dit :
    //   "Si userRepository.findById(1) est appelé → retourne mockUser"
    // ═══════════════════════════════════════════════════════════════════════════
    @Mock private UserRepository      userRepository;
    @Mock private PasswordEncoder     passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private EmailService emailService;
    @Mock private UserMapper          userMapper;
    @Mock private AttendanceService attendanceService;
    @Mock private RoleRepository      roleRepository;
    @Mock private NotificationService notificationService;

    // ═══════════════════════════════════════════════════════════════════════════
    // @InjectMocks = le VRAI objet qu'on teste.
    // Mockito lui injecte automatiquement tous les @Mock ci-dessus.
    // Équivalent de : new AuthenticationService(userRepository, passwordEncoder, ...)
    // ═══════════════════════════════════════════════════════════════════════════
    @InjectMocks
    private AuthenticationService authService;

    // Données de test réutilisables dans tous les tests
    private User               mockUser;
    private Role               employeeRole;
    private RegisterRequestDTO registerRequest;

    // ═══════════════════════════════════════════════════════════════════════════
    // @BeforeEach
    //
    // S'exécute AVANT CHAQUE @Test. Remet les données à zéro pour que chaque
    // test parte d'une situation propre et indépendante des autres tests.
    // ═══════════════════════════════════════════════════════════════════════════
    @BeforeEach
    void setUp() {
        employeeRole = new Role(1, RoleName.EMPLOYEE, "Employee role");

        // Création d'un utilisateur fictif avec .builder() (pattern Lombok)
        mockUser = User.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .nationalId("12345678")
                .passwordHash("$2a$encodedPassword")
                .enabled(false)            // compte pas encore activé
                .firstLogin(true)          // premier login pas encore fait
                .active(true)
                .registrationPending(true)
                .roles(new ArrayList<>(List.of(employeeRole)))
                .activationToken("valid-token")
                .activationTokenExpiry(LocalDateTime.now().plusDays(7))
                .build();

        registerRequest = new RegisterRequestDTO();
        registerRequest.setEmail("jane.doe@example.com");
        registerRequest.setFirstName("Jane");
        registerRequest.setLastName("Doe");
        registerRequest.setNationalId("12345678");
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 1 — register()
    //
    // @Nested = regroupe les tests d'une même méthode pour un rapport clair.
    //
    // CONVENTION DE NOMMAGE DES TESTS :
    //   méthode_scenarioTesté_résultatAttendu
    //   Ex: register_emailAlreadyExists_throwsBusinessException
    //
    // ✅ = cas positif (ça doit marcher)
    // ❌ = cas négatif (ça doit échouer avec la bonne exception)
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("register()")
    class RegisterTests {

        // ─────────────────────────────────────────────────────────────────────
        // STRUCTURE D'UN TEST — Pattern AAA :
        //
        //   GIVEN (Arrange) → préparer le contexte, programmer les mocks
        //   WHEN  (Act)     → appeler la méthode à tester
        //   THEN  (Assert)  → vérifier le résultat
        // ─────────────────────────────────────────────────────────────────────

        @Test
        @DisplayName("✅ Succès — enregistre l'utilisateur et retourne le DTO")
        void register_success() {

            // ── GIVEN ──────────────────────────────────────────────────────────
            // given(mock.méthode(args)).willReturn(valeur)
            // = "quand cette méthode est appelée avec ces args, retourne cette valeur"

            given(userRepository.existsByEmail(registerRequest.getEmail())).willReturn(false);
            given(userRepository.existsByNationalId(registerRequest.getNationalId())).willReturn(false);
            given(userMapper.toEntity(registerRequest)).willReturn(mockUser);
            // anyString() = peu importe la valeur de la chaîne passée
            given(passwordEncoder.encode(anyString())).willReturn("encodedPassword");
            given(roleRepository.findByName(RoleName.EMPLOYEE)).willReturn(Optional.of(employeeRole));
            // any(User.class) = peu importe quel objet User est passé
            given(jwtService.generateActivationToken(any(User.class))).willReturn("activation-token");
            given(userRepository.save(any(User.class))).willReturn(mockUser);

            // ── WHEN ──────────────────────────────────────────────────────────
            RegistrationResponseDTO result = authService.register(registerRequest);

            // ── THEN ──────────────────────────────────────────────────────────
            // assertThat(valeur).isNotNull() → vérifie que la valeur n'est pas null
            assertThat(result).isNotNull();
            // .isEqualTo(attendu) → vérifie l'égalité exacte
            assertThat(result.getEmail()).isEqualTo("jane.doe@example.com");
            assertThat(result.getMessage()).isEqualTo("Registration successful.");
            assertThat(result.isActivationEmailSent()).isFalse();

            // then(mock).should().méthode() → vérifie que la méthode a été appelée
            then(userRepository).should().save(any(User.class));
        }

        @Test
        @DisplayName("❌ Email déjà utilisé — lève BusinessException")
        void register_emailAlreadyExists_throwsBusinessException() {

            // ── GIVEN ──────────────────────────────────────────────────────────
            given(userRepository.existsByEmail(registerRequest.getEmail())).willReturn(true);

            // ── WHEN / THEN ───────────────────────────────────────────────────
            // assertThatThrownBy(() -> code) → vérifie qu'une exception est lancée
            // .isInstanceOf(X.class) → vérifie le type de l'exception
            // .hasMessageContaining("texte") → vérifie le message d'erreur
            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Email already registered");

            // never() = cette méthode ne doit PAS avoir été appelée du tout
            then(userRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("❌ Numéro national déjà utilisé — lève BusinessException")
        void register_nationalIdAlreadyExists_throwsBusinessException() {

            given(userRepository.existsByEmail(registerRequest.getEmail())).willReturn(false);
            given(userRepository.existsByNationalId(registerRequest.getNationalId())).willReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("National ID already registered");

            then(userRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("❌ Rôle EMPLOYEE absent — lève ResourceNotFoundException")
        void register_employeeRoleNotFound_throwsResourceNotFoundException() {

            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(userRepository.existsByNationalId(anyString())).willReturn(false);
            given(userMapper.toEntity(any(RegisterRequestDTO.class)))
                    .willReturn(mockUser);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            // Optional.empty() = simule qu'aucun résultat n'a été trouvé en base
            given(roleRepository.findByName(RoleName.EMPLOYEE)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("EMPLOYEE role not found");
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 2 — getPendingRegistrations()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getPendingRegistrations()")
    class GetPendingRegistrationsTests {

        @Test
        @DisplayName("✅ Retourne la liste des inscriptions en attente")
        void getPendingRegistrations_returnsList() {

            // List.of(mockUser) = liste avec un seul élément
            given(userRepository.findByRegistrationPendingTrue())
                    .willReturn(List.of(mockUser));

            List<RegistrationResponseDTO> result = authService.getPendingRegistrations();

            // .hasSize(1) → la liste contient exactement 1 élément
            assertThat(result).hasSize(1);
            // .get(0) → premier élément
            assertThat(result.get(0).getMessage()).isEqualTo("Waiting for approval");
            assertThat(result.get(0).isActivationEmailSent()).isFalse();
        }

        @Test
        @DisplayName("✅ Aucune inscription en attente — retourne liste vide")
        void getPendingRegistrations_emptyList() {

            // List.of() sans argument = liste vide
            given(userRepository.findByRegistrationPendingTrue()).willReturn(List.of());

            List<RegistrationResponseDTO> result = authService.getPendingRegistrations();

            // .isEmpty() → la liste doit être vide
            assertThat(result).isEmpty();
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 3 — approveRegistration()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("approveRegistration()")
    class ApproveRegistrationTests {

        @Test
        @DisplayName("✅ Approuve l'utilisateur et envoie l'email d'activation")
        void approveRegistration_success() {
            given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));
            // ✅ AJOUT : le service appelle TOUJOURS generateActivationToken
            given(jwtService.generateActivationToken(any())).willReturn("new-token");
            given(userRepository.save(any())).willReturn(mockUser);
            willDoNothing().given(emailService)
                    .sendActivationReminderEmail(anyString(), anyString());

            RegistrationResponseDTO result = authService.approveRegistration(1L);

            assertThat(result.getMessage()).contains("approved");
            assertThat(result.isActivationEmailSent()).isTrue();
            // ✅ verify avec le vrai token généré
            then(emailService).should()
                    .sendActivationReminderEmail(eq(mockUser.getEmail()), eq("new-token"));
        }

        @Test
        @DisplayName("❌ Utilisateur introuvable — lève ResourceNotFoundException")
        void approveRegistration_userNotFound_throwsException() {

            // Optional.empty() = aucun user trouvé avec l'ID 99
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.approveRegistration(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("❌ Compte déjà activé — lève BusinessException")
        void approveRegistration_alreadyActivated_throwsBusinessException() {

            // On modifie mockUser pour simuler un compte déjà activé
            mockUser.setEnabled(true);
            mockUser.setRegistrationPending(false);
            given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> authService.approveRegistration(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Registration already processed");
        }

        @Test
        @DisplayName("✅ Token expiré — régénère un nouveau token avant d'envoyer l'email")
        void approveRegistration_expiredToken_regeneratesToken() {

            // On simule un token expiré en mettant null
            mockUser.setActivationToken(null);
            mockUser.setActivationTokenExpiry(null);
            given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));
            given(jwtService.generateActivationToken(any())).willReturn("regenerated-token");
            given(userRepository.save(any())).willReturn(mockUser);
            willDoNothing().given(emailService).sendActivationReminderEmail(anyString(), any());

            authService.approveRegistration(1L);

            // Vérifier que generateActivationToken() a bien été appelé
            then(jwtService).should().generateActivationToken(any(User.class));
            // Vérifier que l'email a reçu le NOUVEAU token "regenerated-token"
            then(emailService).should().sendActivationReminderEmail(anyString(), eq("regenerated-token"));
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 4 — authenticate() (LOGIN)
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("authenticate()")
    class AuthenticateTests {

        private LoginRequestDTO loginRequest;

        // Ce @BeforeEach s'applique uniquement aux tests DE CE GROUPE
        @BeforeEach
        void setUpLogin() {
            loginRequest = new LoginRequestDTO();
            loginRequest.setEmail("jane.doe@example.com");
            loginRequest.setPassword("plainPassword");
            // Compte prêt à se connecter
            mockUser.setEnabled(true);
            mockUser.setFirstLogin(false);
        }

        @Test
        @DisplayName("✅ Login réussi — retourne le JWT et déclenche le check-in")
        void authenticate_success_returnsJwtAndTriggersCheckIn() {

            given(userRepository.findByEmailIgnoreCase(loginRequest.getEmail()))
                    .willReturn(Optional.of(mockUser));
            // passwordEncoder.matches(plaintext, hash) → true = mot de passe correct
            given(passwordEncoder.matches("plainPassword", mockUser.getPasswordHash()))
                    .willReturn(true);
            given(userRepository.save(any())).willReturn(mockUser);
            given(jwtService.generateAccessToken(any())).willReturn("jwt-token");
            given(userMapper.toResponseDTO(any())).willReturn(new UserResponseDTO());

            JwtResponseDTO result = authService.authenticate(loginRequest);

            assertThat(result.getToken()).isEqualTo("jwt-token");
            assertThat(result.getTokenType()).isEqualTo("Bearer");
            assertThat(result.getMessage()).contains("successful");
            // Le check-in DOIT avoir été appelé avec l'ID du user
            then(attendanceService).should().checkIn(mockUser.getId());
        }

        @Test
        @DisplayName("❌ Email introuvable — lève BusinessException 'Invalid credentials'")
        void authenticate_emailNotFound_throwsBusinessException() {

            given(userRepository.findByEmailIgnoreCase(anyString()))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.authenticate(loginRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid credentials");

            // Le check-in ne doit JAMAIS être appelé si le login échoue
            then(attendanceService).should(never()).checkIn(any());
        }

        @Test
        @DisplayName("❌ Mauvais mot de passe — lève BusinessException 'Invalid credentials'")
        void authenticate_wrongPassword_throwsBusinessException() {

            given(userRepository.findByEmailIgnoreCase(anyString()))
                    .willReturn(Optional.of(mockUser));
            // false → mot de passe incorrect
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

            assertThatThrownBy(() -> authService.authenticate(loginRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid credentials");
        }

        @Test
        @DisplayName("❌ Compte non activé — lève BusinessException")
        void authenticate_accountNotEnabled_throwsBusinessException() {

            mockUser.setEnabled(false); // ← compte désactivé
            given(userRepository.findByEmailIgnoreCase(anyString()))
                    .willReturn(Optional.of(mockUser));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

            assertThatThrownBy(() -> authService.authenticate(loginRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not activated");
        }

        @Test
        @DisplayName("❌ Premier login non effectué — lève BusinessException")
        void authenticate_firstLoginNotDone_throwsBusinessException() {

            mockUser.setEnabled(true);
            mockUser.setFirstLogin(true); // ← doit changer son mot de passe d'abord
            given(userRepository.findByEmailIgnoreCase(anyString()))
                    .willReturn(Optional.of(mockUser));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

            assertThatThrownBy(() -> authService.authenticate(loginRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Password change required");
        }

        @Test
        @DisplayName("✅ Échec du check-in — le login continue quand même")
        void authenticate_checkInFails_loginNotBlocked() {

            given(userRepository.findByEmailIgnoreCase(anyString()))
                    .willReturn(Optional.of(mockUser));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
            given(userRepository.save(any())).willReturn(mockUser);
            given(jwtService.generateAccessToken(any())).willReturn("jwt-token");
            given(userMapper.toResponseDTO(any())).willReturn(new UserResponseDTO());
            // willThrow() → le checkIn() lance une exception
            willThrow(new RuntimeException("DB error")).given(attendanceService).checkIn(any());

            // Le login NE DOIT PAS être bloqué même si checkIn() échoue
            JwtResponseDTO result = authService.authenticate(loginRequest);

            assertThat(result.getToken()).isEqualTo("jwt-token");
        }

        @Test
        @DisplayName("✅ La date du dernier login est mise à jour")
        void authenticate_updatesLastLoginDate() {

            given(userRepository.findByEmailIgnoreCase(anyString()))
                    .willReturn(Optional.of(mockUser));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
            // inv.getArgument(0) = retourne l'objet passé en paramètre à save()
            // → permet de vérifier les modifications faites AVANT le save()
            given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(jwtService.generateAccessToken(any())).willReturn("token");
            given(userMapper.toResponseDTO(any())).willReturn(new UserResponseDTO());

            authService.authenticate(loginRequest);

            // La propriété lastLogin du user doit avoir été mise à aujourd'hui
            assertThat(mockUser.getLastLogin()).isEqualTo(LocalDate.now());
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 5 — activateAccount()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("activateAccount()")
    class ActivateAccountTests {

        private ActivationRequestDTO activationRequest;

        @BeforeEach
        void setUpActivation() {
            activationRequest = new ActivationRequestDTO();
            activationRequest.setToken("valid-token");
            activationRequest.setUsername("janedoe");
            activationRequest.setNewPassword("NewPass123!");
            activationRequest.setConfirmPassword("NewPass123!"); // identique
        }

        @Test
        @DisplayName("✅ Active le compte et retourne un JWT")
        void activateAccount_success_returnsJwt() {

            given(jwtService.isTokenValid("valid-token")).willReturn(true);
            given(jwtService.isActivationToken("valid-token")).willReturn(true);
            given(userRepository.findByActivationToken("valid-token"))
                    .willReturn(Optional.of(mockUser));
            given(passwordEncoder.encode("NewPass123!")).willReturn("encodedNew");
            given(userRepository.save(any())).willReturn(mockUser);
            given(jwtService.generateAccessToken(any())).willReturn("access-jwt");
            given(jwtService.getRemainingTime("access-jwt")).willReturn(86400000L);
            willDoNothing().given(notificationService).notifyWelcome(anyLong());

            JwtResponseDTO result = authService.activateAccount(activationRequest);

            assertThat(result.getToken()).isEqualTo("access-jwt");
            assertThat(result.getMessage()).contains("activated");
            then(notificationService).should().notifyWelcome(mockUser.getId());
            // argThat() = vérification complexe sur l'objet passé à save()
            // On s'assure que le user est bien activé lors de la sauvegarde
            then(userRepository).should().save(argThat(u ->
                    u.isEnabled()                   // compte activé
                            && !u.isFirstLogin()              // premier login effectué
                            && u.getActivationToken() == null)); // token effacé
        }

        @Test
        @DisplayName("❌ Mots de passe non identiques — lève BusinessException")
        void activateAccount_passwordMismatch_throwsBusinessException() {

            activationRequest.setConfirmPassword("DifferentPass!"); // ← différent !

            assertThatThrownBy(() -> authService.activateAccount(activationRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Passwords do not match");

            then(userRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("❌ Nom d'utilisateur trop court — lève BusinessException")
        void activateAccount_shortUsername_throwsBusinessException() {

            activationRequest.setUsername("ab"); // ← 2 caractères, minimum requis = 3
            given(jwtService.isTokenValid("valid-token")).willReturn(true);
            given(jwtService.isActivationToken("valid-token")).willReturn(true);
            given(userRepository.findByActivationToken("valid-token"))
                    .willReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> authService.activateAccount(activationRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("at least 3 characters");
        }

        @Test
        @DisplayName("❌ Token invalide — lève BusinessException")
        void activateAccount_invalidToken_throwsBusinessException() {

            // Token JWT avec signature incorrecte ou expiré au niveau JWT
            given(jwtService.isTokenValid("valid-token")).willReturn(false);

            assertThatThrownBy(() -> authService.activateAccount(activationRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid or expired activation token");
        }

        @Test
        @DisplayName("❌ Token expiré en base — lève BusinessException")
        void activateAccount_expiredTokenInDb_throwsBusinessException() {

            // Le token JWT est valide, mais la date d'expiration en base est dépassée
            mockUser.setActivationTokenExpiry(LocalDateTime.now().minusDays(1)); // ← hier
            given(jwtService.isTokenValid("valid-token")).willReturn(true);
            given(jwtService.isActivationToken("valid-token")).willReturn(true);
            given(userRepository.findByActivationToken("valid-token"))
                    .willReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> authService.activateAccount(activationRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("❌ Compte déjà activé — lève BusinessException")
        void activateAccount_alreadyEnabled_throwsBusinessException() {

            mockUser.setEnabled(true); // ← déjà activé
            given(jwtService.isTokenValid("valid-token")).willReturn(true);
            given(jwtService.isActivationToken("valid-token")).willReturn(true);
            given(userRepository.findByActivationToken("valid-token"))
                    .willReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> authService.activateAccount(activationRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already activated");
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 6 — validateActivationTokenApi()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("validateActivationTokenApi()")
    class ValidateTokenTests {

        @Test
        @DisplayName("✅ Token valide — retourne true")
        void validateToken_validToken_returnsTrue() {

            given(jwtService.isTokenValid("valid-token")).willReturn(true);
            given(jwtService.isActivationToken("valid-token")).willReturn(true);
            given(userRepository.findByActivationToken("valid-token"))
                    .willReturn(Optional.of(mockUser));
            // mockUser.enabled=false et tokenExpiry dans le futur → token utilisable

            boolean result = authService.validateActivationTokenApi("valid-token");

            // .isTrue() → vérifie que la valeur est true
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("❌ Token invalide — retourne false")
        void validateToken_invalidToken_returnsFalse() {

            given(jwtService.isTokenValid("bad-token")).willReturn(false);

            boolean result = authService.validateActivationTokenApi("bad-token");

            // .isFalse() → vérifie que la valeur est false
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("❌ Compte déjà activé — retourne false")
        void validateToken_accountAlreadyEnabled_returnsFalse() {

            mockUser.setEnabled(true); // ← compte déjà activé = token inutile
            given(jwtService.isTokenValid("valid-token")).willReturn(true);
            given(jwtService.isActivationToken("valid-token")).willReturn(true);
            given(userRepository.findByActivationToken("valid-token"))
                    .willReturn(Optional.of(mockUser));

            boolean result = authService.validateActivationTokenApi("valid-token");

            assertThat(result).isFalse();
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 7 — resendActivationEmail()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("resendActivationEmail()")
    class ResendActivationTests {

        @Test
        @DisplayName("✅ Renvoie l'email d'activation avec un nouveau token")
        void resendActivationEmail_success() {

            given(userRepository.findByEmail("jane.doe@example.com"))
                    .willReturn(Optional.of(mockUser));
            given(jwtService.generateActivationToken(any())).willReturn("new-activation-token");
            given(userRepository.save(any())).willReturn(mockUser);
            willDoNothing().given(emailService)
                    .sendActivationReminderEmail(anyString(), anyString());

            ResendActivationResponseDTO result =
                    authService.resendActivationEmail("jane.doe@example.com");

            assertThat(result.getEmail()).isEqualTo("jane.doe@example.com");
            assertThat(result.getMessage()).contains("resent");
            // L'email DOIT avoir été envoyé avec le nouveau token exact
            then(emailService).should()
                    .sendActivationReminderEmail("jane.doe@example.com", "new-activation-token");
        }

        @Test
        @DisplayName("❌ Email introuvable — lève ResourceNotFoundException")
        void resendActivationEmail_userNotFound_throwsException() {

            given(userRepository.findByEmail("unknown@example.com"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resendActivationEmail("unknown@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");

            // Aucun email ne doit être envoyé si le user n'existe pas
            then(emailService).should(never()).sendActivationReminderEmail(any(), any());
        }

        @Test
        @DisplayName("❌ Compte déjà activé — lève BusinessException")
        void resendActivationEmail_accountAlreadyEnabled_throwsException() {

            mockUser.setEnabled(true); // ← inutile d'envoyer si déjà activé
            given(userRepository.findByEmail("jane.doe@example.com"))
                    .willReturn(Optional.of(mockUser));

            assertThatThrownBy(() -> authService.resendActivationEmail("jane.doe@example.com"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already activated");

            then(emailService).should(never()).sendActivationReminderEmail(any(), any());
        }
    }
}