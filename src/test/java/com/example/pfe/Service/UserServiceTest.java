package com.example.pfe.Service;

import com.example.pfe.Repository.RoleRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.ChangePasswordDTO;
import com.example.pfe.dto.UserRequestDTO;
import com.example.pfe.dto.UserResponseDTO;
import com.example.pfe.dto.UserStatsDTO;
import com.example.pfe.entities.Role;
import com.example.pfe.entities.User;
import com.example.pfe.enums.RoleName;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService - Tests Unitaires")
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMapper userMapper;
    @Mock private EmailService emailService;
    @Mock private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private User buildUser(Long id, String email, boolean enabled, boolean active) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setUsername(email);
        u.setFirstName("John");
        u.setLastName("Doe");
        u.setEnabled(enabled);
        u.setActive(active);
        u.setAccountNonLocked(true);
        u.setAccountNonExpired(true);
        u.setCredentialsNonExpired(true);
        u.setRegistrationPending(false);
        u.setRoles(new ArrayList<>());
        return u;
    }

    private UserRequestDTO buildRequestDTO(String email) {
        UserRequestDTO dto = new UserRequestDTO();
        dto.setEmail(email);
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setNationalId("12345678");
        dto.setRoleNames(List.of("EMPLOYEE"));
        return dto;
    }

    private Role buildRole(RoleName name) {
        Role role = new Role();
        role.setId(1);
        role.setName(name);
        return role;
    }

    @BeforeEach
    void injectConfigValues() {
        ReflectionTestUtils.setField(userService, "uploadDir", "uploads/avatars");
        ReflectionTestUtils.setField(userService, "baseUrl", "http://localhost:8080");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // createUser
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("createUser()")
    class CreateUser {

        @Test
        @DisplayName("Crée un utilisateur avec succès et envoie l'email d'activation")
        void shouldCreateUserSuccessfully() {
            // Arrange
            UserRequestDTO dto = buildRequestDTO("new@test.com");
            User entity = buildUser(1L, "new@test.com", false, false);
            UserResponseDTO responseDTO = new UserResponseDTO();

            when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);
            when(userRepository.existsByNationalId(dto.getNationalId())).thenReturn(false);
            when(userMapper.toEntity(dto)).thenReturn(entity);
            when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(buildRole(RoleName.EMPLOYEE)));
            when(jwtService.generateTokenWithExpiration(any(), anyString(), anyLong())).thenReturn("token123");
            when(userRepository.save(any(User.class))).thenReturn(entity);
            when(userMapper.toResponseDTO(entity)).thenReturn(responseDTO);

            // Act
            UserResponseDTO result = userService.createUser(dto);

            // Assert
            assertThat(result).isNotNull();
            verify(userRepository).save(any(User.class));
            verify(emailService).sendWelcomeEmail(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Lève BusinessException si l'email existe déjà")
        void shouldThrowWhenEmailAlreadyExists() {
            UserRequestDTO dto = buildRequestDTO("existing@test.com");
            when(userRepository.existsByEmail(dto.getEmail())).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Email already exists");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Lève BusinessException si le NationalId existe déjà")
        void shouldThrowWhenNationalIdAlreadyExists() {
            UserRequestDTO dto = buildRequestDTO("new@test.com");
            when(userRepository.existsByEmail(dto.getEmail())).thenReturn(false);
            when(userRepository.existsByNationalId(dto.getNationalId())).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("National ID already exists");
        }

        @Test
        @DisplayName("Assigne le rôle EMPLOYEE par défaut si aucun rôle n'est fourni")
        void shouldAssignDefaultEmployeeRole() {
            UserRequestDTO dto = buildRequestDTO("new@test.com");
            dto.setRoleNames(null); // pas de rôle fourni

            User entity = buildUser(1L, "new@test.com", false, false);
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(userRepository.existsByNationalId(any())).thenReturn(false);
            when(userMapper.toEntity(dto)).thenReturn(entity);
            when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(buildRole(RoleName.EMPLOYEE)));
            when(jwtService.generateTokenWithExpiration(any(), any(), anyLong())).thenReturn("tok");
            when(userRepository.save(any())).thenReturn(entity);
            when(userMapper.toResponseDTO(entity)).thenReturn(new UserResponseDTO());

            userService.createUser(dto);

            verify(roleRepository).findByName(RoleName.EMPLOYEE);
        }

        @Test
        @DisplayName("Continue même si l'envoi d'email échoue")
        void shouldContinueEvenWhenEmailFails() {
            UserRequestDTO dto = buildRequestDTO("new@test.com");
            User entity = buildUser(1L, "new@test.com", false, false);

            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(userRepository.existsByNationalId(any())).thenReturn(false);
            when(userMapper.toEntity(dto)).thenReturn(entity);
            when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(buildRole(RoleName.EMPLOYEE)));
            when(jwtService.generateTokenWithExpiration(any(), any(), anyLong())).thenReturn("tok");
            when(userRepository.save(any())).thenReturn(entity);
            when(userMapper.toResponseDTO(entity)).thenReturn(new UserResponseDTO());
            doThrow(new RuntimeException("SMTP error")).when(emailService)
                    .sendWelcomeEmail(any(), any(), any(), any());

            // Doit ne pas lever d'exception
            assertThatCode(() -> userService.createUser(dto)).doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getUserById
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getUserById()")
    class GetUserById {

        @Test
        @DisplayName("Retourne le DTO quand l'utilisateur existe")
        void shouldReturnUserWhenFound() {
            User user = buildUser(1L, "john@test.com", true, true);
            UserResponseDTO dto = new UserResponseDTO();

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(userMapper.toResponseDTO(user)).thenReturn(dto);

            UserResponseDTO result = userService.getUserById(1L);

            assertThat(result).isSameAs(dto);
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException quand l'utilisateur n'existe pas")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getAllUsers
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsers {

        @Test
        @DisplayName("Retourne une page d'utilisateurs mappée en DTOs")
        void shouldReturnPageOfUsers() {
            User user = buildUser(1L, "a@test.com", true, true);
            Page<User> page = new PageImpl<>(List.of(user));
            Pageable pageable = PageRequest.of(0, 10);

            when(userRepository.findAll(pageable)).thenReturn(page);
            when(userMapper.toResponseDTO(user)).thenReturn(new UserResponseDTO());

            Page<UserResponseDTO> result = userService.getAllUsers(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // updateUser
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateUser()")
    class UpdateUser {

        @Test
        @DisplayName("Met à jour un utilisateur existant avec succès")
        void shouldUpdateUserSuccessfully() {
            User existing = buildUser(1L, "a@test.com", true, true);
            UserRequestDTO dto = buildRequestDTO("a@test.com");
            UserResponseDTO responseDTO = new UserResponseDTO();

            when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(buildRole(RoleName.EMPLOYEE)));
            when(userRepository.save(existing)).thenReturn(existing);
            when(userMapper.toResponseDTO(existing)).thenReturn(responseDTO);

            UserResponseDTO result = userService.updateUser(1L, dto);

            assertThat(result).isSameAs(responseDTO);
            verify(userMapper).updateEntityFromDTO(dto, existing);
            verify(userRepository).save(existing);
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si l'utilisateur n'existe pas")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateUser(999L, buildRequestDTO("x@x.com")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // disableUser
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("disableUser()")
    class DisableUser {

        @Test
        @DisplayName("Désactive un utilisateur actif")
        void shouldDisableActiveUser() {
            User user = buildUser(1L, "a@test.com", true, true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.disableUser(1L);

            assertThat(user.getActive()).isFalse();
            verify(userRepository).save(user);
            verify(emailService).sendAccountDisabledEmail(anyString(), anyString());
        }

        @Test
        @DisplayName("Lève BusinessException si l'utilisateur est déjà désactivé")
        void shouldThrowWhenAlreadyDisabled() {
            User user = buildUser(1L, "a@test.com", true, false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.disableUser(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already disabled");
        }

        @Test
        @DisplayName("Continue si l'email de désactivation échoue")
        void shouldContinueEvenWhenEmailFails() {
            User user = buildUser(1L, "a@test.com", true, true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            doThrow(new RuntimeException("mail error")).when(emailService)
                    .sendAccountDisabledEmail(any(), any());

            assertThatCode(() -> userService.disableUser(1L)).doesNotThrowAnyException();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // approveUser
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("approveUser()")
    class ApproveUser {

        @Test
        @DisplayName("Approuve un utilisateur en attente")
        void shouldApproveUser() {
            User user = buildUser(1L, "pending@test.com", false, false);
            user.setRegistrationPending(true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.approveUser(1L);

            assertThat(user.isEnabled()).isTrue();
            assertThat(user.getActive()).isTrue();
            assertThat(user.isRegistrationPending()).isFalse();
            assertThat(user.getActivationToken()).isNull();
            verify(userRepository).save(user);
            verify(emailService).sendAccountApprovedEmail(anyString(), anyString());
        }

        @Test
        @DisplayName("Lève BusinessException si l'utilisateur est déjà approuvé")
        void shouldThrowWhenAlreadyApproved() {
            User user = buildUser(1L, "a@test.com", true, true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.approveUser(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already approved");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // rejectUser
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("rejectUser()")
    class RejectUser {

        @Test
        @DisplayName("Supprime un utilisateur en attente")
        void shouldDeletePendingUser() {
            User user = buildUser(1L, "pending@test.com", false, false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.rejectUser(1L);

            verify(userRepository).delete(user);
        }

        @Test
        @DisplayName("Lève BusinessException si l'utilisateur est déjà approuvé")
        void shouldThrowWhenUserAlreadyApproved() {
            User user = buildUser(1L, "a@test.com", true, true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.rejectUser(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot reject an approved user");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // enableUser
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("enableUser()")
    class EnableUser {

        @Test
        @DisplayName("Réactive un utilisateur approuvé mais désactivé")
        void shouldEnableUser() {
            User user = buildUser(1L, "a@test.com", true, false); // enabled=true, active=false
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.enableUser(1L);

            assertThat(user.getActive()).isTrue();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Lève BusinessException si l'utilisateur n'a jamais été approuvé (enabled=false)")
        void shouldThrowWhenNotRegistered() {
            User user = buildUser(1L, "a@test.com", false, false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.enableUser(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not completed registration");
        }

        @Test
        @DisplayName("Lève BusinessException si l'utilisateur est déjà actif")
        void shouldThrowWhenAlreadyActive() {
            User user = buildUser(1L, "a@test.com", true, true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.enableUser(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already active");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // reactivateUser
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("reactivateUser()")
    class ReactivateUser {

        @Test
        @DisplayName("Met active=true et enabled=true")
        void shouldReactivate() {
            User user = buildUser(1L, "a@test.com", false, false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.reactivateUser(1L);

            assertThat(user.getActive()).isTrue();
            assertThat(user.isEnabled()).isTrue();
            verify(userRepository).save(user);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // resetUserPassword
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("resetUserPassword()")
    class ResetUserPassword {

        @Test
        @DisplayName("Réinitialise le mot de passe et envoie un email")
        void shouldResetPasswordAndSendEmail() {
            User user = buildUser(1L, "a@test.com", true, true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");

            userService.resetUserPassword(1L);

            assertThat(user.getPasswordHash()).isEqualTo("hashed");
            assertThat(user.isFirstLogin()).isTrue();
            verify(emailService).sendPasswordResetEmail(eq("a@test.com"), anyString(), anyString());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // changePassword
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("changePassword()")
    class ChangePassword {

        private ChangePasswordDTO buildChangeDTO(String current, String newPwd, String confirm) {
            ChangePasswordDTO dto = new ChangePasswordDTO();
            dto.setCurrentPassword(current);
            dto.setNewPassword(newPwd);
            dto.setConfirmPassword(confirm);
            return dto;
        }

        @Test
        @DisplayName("Change le mot de passe avec succès")
        void shouldChangePasswordSuccessfully() {
            User user = buildUser(1L, "a@test.com", true, true);
            user.setPasswordHash("$hashed_old");

            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass1!", "$hashed_old")).thenReturn(true);
            when(passwordEncoder.matches("NewPass1!", "$hashed_old")).thenReturn(false);
            when(passwordEncoder.encode("NewPass1!")).thenReturn("$hashed_new");

            userService.changePassword("a@test.com", buildChangeDTO("OldPass1!", "NewPass1!", "NewPass1!"));

            assertThat(user.getPasswordHash()).isEqualTo("$hashed_new");
            assertThat(user.isFirstLogin()).isFalse();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Lève BusinessException si les mots de passe ne correspondent pas")
        void shouldThrowWhenPasswordsDoNotMatch() {
            assertThatThrownBy(() ->
                    userService.changePassword("a@test.com", buildChangeDTO("old", "new1", "new2")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("do not match");
        }

        @Test
        @DisplayName("Lève BusinessException si l'ancien mot de passe est incorrect")
        void shouldThrowWhenCurrentPasswordWrong() {
            User user = buildUser(1L, "a@test.com", true, true);
            user.setPasswordHash("$hashed_old");

            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPass", "$hashed_old")).thenReturn(false);

            assertThatThrownBy(() ->
                    userService.changePassword("a@test.com", buildChangeDTO("WrongPass", "New1!", "New1!")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("incorrect");
        }

        @Test
        @DisplayName("Lève BusinessException si le nouveau mot de passe est identique à l'ancien")
        void shouldThrowWhenNewPasswordSameAsOld() {
            User user = buildUser(1L, "a@test.com", true, true);
            user.setPasswordHash("$hashed_old");

            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Same1!", "$hashed_old")).thenReturn(true);

            assertThatThrownBy(() ->
                    userService.changePassword("a@test.com", buildChangeDTO("Same1!", "Same1!", "Same1!")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("different");
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si l'utilisateur n'existe pas")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    userService.changePassword("ghost@test.com", buildChangeDTO("a", "b", "b")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getUserStats
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getUserStats()")
    class GetUserStats {

        @Test
        @DisplayName("Retourne les statistiques correctes")
        void shouldReturnCorrectStats() {
            when(userRepository.countPendingUsers()).thenReturn(3L);
            when(userRepository.countActiveUsers()).thenReturn(10L);
            when(userRepository.countDisabledUsers()).thenReturn(2L);
            when(userRepository.countLockedUsers()).thenReturn(1L);
            when(userRepository.count()).thenReturn(16L);

            UserStatsDTO stats = userService.getUserStats();

            assertThat(stats.getPending()).isEqualTo(3L);
            assertThat(stats.getActive()).isEqualTo(10L);
            assertThat(stats.getDisabled()).isEqualTo(2L);
            assertThat(stats.getLocked()).isEqualTo(1L);
            assertThat(stats.getTotal()).isEqualTo(16L);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // searchUsers
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("searchUsers(keyword, pageable)")
    class SearchUsersWithPagination {

        @Test
        @DisplayName("Retourne tous les utilisateurs si le keyword est vide")
        void shouldReturnAllWhenKeywordEmpty() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> page = new PageImpl<>(List.of(buildUser(1L, "a@test.com", true, true)));

            when(userRepository.findAll(pageable)).thenReturn(page);
            when(userMapper.toResponseDTO(any())).thenReturn(new UserResponseDTO());

            Page<UserResponseDTO> result = userService.searchUsers("  ", pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(userRepository).findAll(pageable);
        }

        @Test
        @DisplayName("Recherche par mot-clé via le repository")
        void shouldSearchByKeyword() {
            Pageable pageable = PageRequest.of(0, 10);
            User user = buildUser(1L, "john@test.com", true, true);
            Page<User> page = new PageImpl<>(List.of(user));

            when(userRepository.searchByKeyword("john", pageable)).thenReturn(page);
            when(userMapper.toResponseDTO(user)).thenReturn(new UserResponseDTO());

            Page<UserResponseDTO> result = userService.searchUsers("john", pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(userRepository).searchByKeyword("john", pageable);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getUsersByStatus
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getUsersByStatus()")
    class GetUsersByStatus {

        @Test
        @DisplayName("Retourne les utilisateurs filtrés par statut")
        void shouldReturnUsersByStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            User user = buildUser(1L, "a@test.com", true, true);
            Page<User> page = new PageImpl<>(List.of(user));

            when(userRepository.findByStatus("ACTIVE", pageable)).thenReturn(page);
            when(userMapper.toResponseDTO(user)).thenReturn(new UserResponseDTO());

            Page<UserResponseDTO> result = userService.getUsersByStatus("ACTIVE", pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // fixAllUserStatus
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("fixAllUserStatus()")
    class FixAllUserStatus {

        @Test
        @DisplayName("Met accountNonLocked=true pour tous les utilisateurs")
        void shouldFixAccountNonLocked() {
            User u1 = buildUser(1L, "a@test.com", true, false);
            u1.setAccountNonLocked(false);
            User u2 = buildUser(2L, "b@test.com", false, false);
            u2.setAccountNonLocked(false);

            when(userRepository.findAll()).thenReturn(List.of(u1, u2));

            userService.fixAllUserStatus();

            assertThat(u1.isAccountNonLocked()).isTrue();
            assertThat(u2.isAccountNonLocked()).isTrue();
            verify(userRepository).saveAll(List.of(u1, u2));
        }

        @Test
        @DisplayName("Les utilisateurs enabled=true deviennent active=true")
        void shouldSetActiveForEnabledUsers() {
            User u = buildUser(1L, "a@test.com", true, false);
            when(userRepository.findAll()).thenReturn(List.of(u));

            userService.fixAllUserStatus();

            assertThat(u.getActive()).isTrue();
            assertThat(u.isRegistrationPending()).isFalse();
        }

        @Test
        @DisplayName("Les utilisateurs enabled=false deviennent registrationPending=true")
        void shouldSetPendingForNonEnabledUsers() {
            User u = buildUser(1L, "b@test.com", false, false);
            when(userRepository.findAll()).thenReturn(List.of(u));

            userService.fixAllUserStatus();

            assertThat(u.getActive()).isFalse();
            assertThat(u.isRegistrationPending()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getAvailableManagers
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getAvailableManagers()")
    class GetAvailableManagers {

        @Test
        @DisplayName("Retourne la liste des managers disponibles")
        void shouldReturnManagers() {
            User manager = buildUser(1L, "manager@test.com", true, true);
            when(userRepository.findAvailableManagers()).thenReturn(List.of(manager));
            when(userMapper.toResponseDTO(manager)).thenReturn(new UserResponseDTO());

            List<UserResponseDTO> result = userService.getAvailableManagers();

            assertThat(result).hasSize(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // setUserRelations — directManager / assignedProjectManager
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Relations manager lors de la création")
    class UserRelations {

        @Test
        @DisplayName("Assigne le directManager si fourni")
        void shouldAssignDirectManager() {
            UserRequestDTO dto = buildRequestDTO("new@test.com");
            dto.setDirectManagerId(5L);

            User entity = buildUser(1L, "new@test.com", false, false);
            User manager = buildUser(5L, "manager@test.com", true, true);

            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(userRepository.existsByNationalId(any())).thenReturn(false);
            when(userMapper.toEntity(dto)).thenReturn(entity);
            when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(buildRole(RoleName.EMPLOYEE)));
            when(jwtService.generateTokenWithExpiration(any(), any(), anyLong())).thenReturn("tok");
            when(userRepository.findById(5L)).thenReturn(Optional.of(manager));
            when(userRepository.save(any())).thenReturn(entity);
            when(userMapper.toResponseDTO(any())).thenReturn(new UserResponseDTO());

            userService.createUser(dto);

            assertThat(entity.getDirectManager()).isSameAs(manager);
        }
    }
}