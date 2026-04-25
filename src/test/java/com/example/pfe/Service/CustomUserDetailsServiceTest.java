package com.example.pfe.Service;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.entities.Role;
import com.example.pfe.entities.User;
import com.example.pfe.enums.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService — Tests Unitaires")
class CustomUserDetailsServiceTest {

    // Un seul mock : on dépend uniquement de UserRepository
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    // Données de test réutilisables
    private User mockUser;

    @BeforeEach
    void setUp() {
        // Création d'un rôle EMPLOYEE
        Role employeeRole = new Role(1, RoleName.EMPLOYEE, "Employee");

        // Création d'un utilisateur fictif activé, avec un rôle
        mockUser = User.builder()
                .id(1L)
                .email("jane.doe@example.com")
                .passwordHash("$2a$encodedPassword")
                .enabled(true)               // compte activé
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .accountNonLocked(true)
                .roles(new ArrayList<>(List.of(employeeRole)))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1 — Cas normal : utilisateur trouvé et activé
    //
    // Ce que ce test vérifie :
    //   ✅ loadUserByUsername() retourne bien un UserDetails
    //   ✅ Le username du UserDetails = l'email de l'utilisateur
    //   ✅ Le password du UserDetails = le hash stocké en base
    //   ✅ Le rôle est converti en format "ROLE_EMPLOYEE"
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("✅ Utilisateur trouvé et activé — retourne UserDetails avec le bon rôle")
    void loadUserByUsername_success_returnsUserDetails() {

        // GIVEN : la base de données retourne mockUser pour cet email
        given(userRepository.findByEmail("jane.doe@example.com"))
                .willReturn(Optional.of(mockUser));

        // WHEN : on charge l'utilisateur comme Spring Security le ferait
        UserDetails result = userDetailsService.loadUserByUsername("jane.doe@example.com");

        // THEN : on vérifie chaque champ important du UserDetails retourné

        // Le username doit être l'email
        assertThat(result.getUsername()).isEqualTo("jane.doe@example.com");

        // Le password doit être le hash (pas le mot de passe en clair !)
        assertThat(result.getPassword()).isEqualTo("$2a$encodedPassword");

        // Le compte doit être activé
        assertThat(result.isEnabled()).isTrue();

        // Le rôle doit être préfixé "ROLE_" comme Spring Security l'exige
        // .map(GrantedAuthority::getAuthority) = récupère le nom de chaque rôle
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_EMPLOYEE");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2 — Email introuvable en base
    //
    // Ce que ce test vérifie :
    //   ❌ Si l'email n'existe pas → UsernameNotFoundException est levée
    //   ❌ Le message d'erreur contient l'email pour faciliter le débogage
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("❌ Email introuvable — lève UsernameNotFoundException")
    void loadUserByUsername_userNotFound_throwsException() {

        // GIVEN : aucun utilisateur trouvé avec cet email
        given(userRepository.findByEmail("unknown@example.com"))
                .willReturn(Optional.empty());

        // WHEN / THEN : l'exception doit être levée avec le bon message
        assertThatThrownBy(() ->
                userDetailsService.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknown@example.com");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3 — Compte désactivé (enabled = false)
    //
    // Ce que ce test vérifie :
    //   ❌ Si le compte n'est pas activé → UsernameNotFoundException est levée
    //   ❌ Spring Security ne doit pas permettre la connexion d'un compte désactivé
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("❌ Compte désactivé — lève UsernameNotFoundException")
    void loadUserByUsername_accountDisabled_throwsException() {

        // GIVEN : l'utilisateur existe mais son compte n'est pas activé
        mockUser.setEnabled(false); // ← on désactive le compte
        given(userRepository.findByEmail("jane.doe@example.com"))
                .willReturn(Optional.of(mockUser));

        // WHEN / THEN : le service doit refuser de charger un compte désactivé
        assertThatThrownBy(() ->
                userDetailsService.loadUserByUsername("jane.doe@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("disabled");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4 — Utilisateur avec plusieurs rôles
    //
    // Ce que ce test vérifie :
    //   ✅ Si un user a 2 rôles, les 2 sont bien convertis avec le préfixe "ROLE_"
    //   ✅ Utile pour un ADMIN qui a aussi le rôle EMPLOYEE par exemple
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("✅ Utilisateur avec plusieurs rôles — tous convertis en ROLE_*")
    void loadUserByUsername_multipleRoles_allConverted() {

        // GIVEN : on ajoute un deuxième rôle ADMIN à l'utilisateur
        Role adminRole = new Role(2, RoleName.ADMIN, "Admin");
        mockUser.getRoles().add(adminRole); // l'user a maintenant EMPLOYEE + ADMIN

        given(userRepository.findByEmail("jane.doe@example.com"))
                .willReturn(Optional.of(mockUser));

        // WHEN
        UserDetails result = userDetailsService.loadUserByUsername("jane.doe@example.com");

        // THEN : les deux rôles doivent être présents avec le préfixe "ROLE_"
        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_EMPLOYEE", "ROLE_ADMIN");
        //  containsExactlyInAnyOrder = l'ordre n'a pas d'importance, mais les 2 doivent être là
    }
}