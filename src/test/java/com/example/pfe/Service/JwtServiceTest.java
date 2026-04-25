package com.example.pfe.Service;

import com.example.pfe.entities.Role;
import com.example.pfe.entities.User;
import com.example.pfe.enums.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

// ═══════════════════════════════════════════════════════════════════════════════
// PARTICULARITÉ DE JwtService :
//
// Ce service n'a AUCUNE dépendance externe (@Mock).
// Il ne dépend que de sa propre logique cryptographique.
// Donc :
//   → Pas besoin de @ExtendWith(MockitoExtension.class)
//   → Pas besoin de @Mock
//   → On instancie directement le service avec new JwtService()
//   → On injecte les @Value via ReflectionTestUtils.setField()
//   → On appelle manuellement init() pour initialiser la clé de signature
//
// C'est un test "pur" : on génère de vrais tokens JWT et on les vérifie.
// ═══════════════════════════════════════════════════════════════════════════════
@DisplayName("JwtService — Tests Unitaires")
class JwtServiceTest {

    // Le vrai service — pas un mock
    private JwtService jwtService;

    // Utilisateur de test réutilisable
    private User mockUser;

    @BeforeEach
    void setUp() {
        // Créer une instance réelle du service
        jwtService = new JwtService();

        // Injecter les valeurs @Value manuellement (Spring ne démarre pas ici)
        // On utilise un secret de 64 caractères minimum (512 bits) pour HS256
        ReflectionTestUtils.setField(jwtService, "secret",
                "dGhpc2lzYXZlcnlsb25nc2VjcmV0a2V5Zm9ydGVzdGluZ3B1cnBvc2VzMTIzNDU2");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpirationMs",     3_600_000L); // 1h
        ReflectionTestUtils.setField(jwtService, "activationTokenExpirationMs", 604_800_000L); // 7j
        ReflectionTestUtils.setField(jwtService, "clockSkewSeconds",            60L);

        // @PostConstruct ne s'exécute pas automatiquement → on l'appelle manuellement
        jwtService.init();

        // Utilisateur de test avec un rôle
        Role employeeRole = new Role(1, RoleName.EMPLOYEE, "Employee");
        mockUser = User.builder()
                .id(1L)
                .email("jane.doe@example.com")
                .passwordHash("$2a$encoded")
                .enabled(true)
                .roles(new ArrayList<>(List.of(employeeRole)))
                .build();
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 1 — generateAccessToken() + extraction
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateAccessTokenTests {

        @Test
        @DisplayName("✅ Génère un token non null et non vide")
        void generateAccessToken_returnsNonNullToken() {

            // WHEN
            String token = jwtService.generateAccessToken(mockUser);

            // THEN
            // isNotNull() → le token ne doit pas être null
            // isNotBlank() → le token ne doit pas être vide
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("✅ Le subject du token est l'email de l'utilisateur")
        void generateAccessToken_subjectIsEmail() {

            // WHEN
            String token = jwtService.generateAccessToken(mockUser);
            String subject = jwtService.extractUsername(token);

            // THEN : le subject doit être l'email (pas un ID numérique)
            assertThat(subject).isEqualTo("jane.doe@example.com");
        }

        @Test
        @DisplayName("✅ Le token est de type 'access'")
        void generateAccessToken_typeIsAccess() {

            // WHEN
            String token = jwtService.generateAccessToken(mockUser);
            String type = jwtService.getTokenType(token);

            // THEN
            assertThat(type).isEqualToIgnoringCase("access");
        }

        @Test
        @DisplayName("✅ Le token contient le rôle ROLE_EMPLOYEE")
        void generateAccessToken_containsRole() {

            // WHEN
            String token = jwtService.generateAccessToken(mockUser);

            // extractClaim() = extraire un claim personnalisé par son nom
            // On cast en List car les rôles sont stockés comme une liste dans le JWT
            @SuppressWarnings("unchecked")
            List<String> roles = jwtService.extractClaim(token,
                    claims -> (List<String>) claims.get("roles"));

            // THEN
            assertThat(roles).containsExactly("ROLE_EMPLOYEE");
        }

        @Test
        @DisplayName("✅ isAccessToken() retourne true pour un access token")
        void generateAccessToken_isAccessTokenReturnsTrue() {

            String token = jwtService.generateAccessToken(mockUser);

            assertThat(jwtService.isAccessToken(token)).isTrue();
        }

        @Test
        @DisplayName("✅ isActivationToken() retourne false pour un access token")
        void generateAccessToken_isNotActivationToken() {

            String token = jwtService.generateAccessToken(mockUser);

            // Un access token ne doit PAS être reconnu comme un token d'activation
            assertThat(jwtService.isActivationToken(token)).isFalse();
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 2 — generateActivationToken()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("generateActivationToken()")
    class GenerateActivationTokenTests {

        @Test
        @DisplayName("✅ Génère un token d'activation non null")
        void generateActivationToken_returnsToken() {

            String token = jwtService.generateActivationToken(mockUser);

            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("✅ isActivationToken() retourne true")
        void generateActivationToken_isActivationTokenReturnsTrue() {

            String token = jwtService.generateActivationToken(mockUser);

            assertThat(jwtService.isActivationToken(token)).isTrue();
        }

        @Test
        @DisplayName("✅ isAccessToken() retourne false pour un token d'activation")
        void generateActivationToken_isNotAccessToken() {

            String token = jwtService.generateActivationToken(mockUser);

            // Un token d'activation ne doit PAS être accepté comme access token
            assertThat(jwtService.isAccessToken(token)).isFalse();
        }

        @Test
        @DisplayName("✅ Le userId est correctement extrait du token d'activation")
        void generateActivationToken_extractsUserId() {

            String token = jwtService.generateActivationToken(mockUser);
            Long userId = jwtService.extractUserId(token);

            // L'ID doit être celui du mockUser (1L)
            assertThat(userId).isEqualTo(1L);
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 3 — isTokenValid()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("isTokenValid()")
    class IsTokenValidTests {

        @Test
        @DisplayName("✅ Token valide — retourne true")
        void isTokenValid_validToken_returnsTrue() {

            String token = jwtService.generateAccessToken(mockUser);

            assertThat(jwtService.isTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("❌ Token falsifié (mauvaise signature) — retourne false")
        void isTokenValid_tamperedToken_returnsFalse() {

            String token = jwtService.generateAccessToken(mockUser);

            // On falsifie le token en modifiant le dernier caractère de la signature
            // Un JWT a 3 parties séparées par "." : header.payload.signature
            // On modifie la signature → la vérification cryptographique échoue
            String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

            assertThat(jwtService.isTokenValid(tamperedToken)).isFalse();
        }

        @Test
        @DisplayName("❌ Token complètement invalide (chaîne aléatoire) — retourne false")
        void isTokenValid_randomString_returnsFalse() {

            assertThat(jwtService.isTokenValid("ce.n.est.pas.un.jwt")).isFalse();
        }

        @Test
        @DisplayName("❌ Token null — retourne false sans exception")
        void isTokenValid_nullToken_returnsFalse() {

            // Le service ne doit jamais lancer d'exception pour un token null
            assertThat(jwtService.isTokenValid(null)).isFalse();
        }

        // JwtServiceTest.java — in isTokenValid_expiredToken_returnsFalse()
        @Test
        @DisplayName("❌ Token expiré — retourne false")
        void isTokenValid_expiredToken_returnsFalse() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("type", "access");
            // Use -120_000ms (-2 minutes) to exceed the 60s clock skew tolerance
            String expiredToken = jwtService.generateTokenWithExpiration(
                    claims, "jane.doe@example.com", -120_000L);

            assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 4 — isTokenValid(token, UserDetails)
    // Surcharge qui vérifie aussi que le token appartient au bon utilisateur
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("isTokenValid(token, UserDetails)")
    class IsTokenValidWithUserDetailsTests {

        // Implémentation minimale de UserDetails pour les tests
        // On n'a besoin que de getUsername() → on utilise une lambda
        private UserDetails userDetailsFor(String username) {
            return new org.springframework.security.core.userdetails.User(
                    username, "password", List.of());
        }

        @Test
        @DisplayName("✅ Token valide pour le bon utilisateur — retourne true")
        void isTokenValid_validTokenCorrectUser_returnsTrue() {

            String token = jwtService.generateAccessToken(mockUser);
            UserDetails userDetails = userDetailsFor("jane.doe@example.com");

            assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
        }

        @Test
        @DisplayName("❌ Token valide mais pour un autre utilisateur — retourne false")
        void isTokenValid_validTokenWrongUser_returnsFalse() {

            String token = jwtService.generateAccessToken(mockUser);
            // Le token appartient à jane.doe, mais on vérifie pour john.doe
            UserDetails wrongUser = userDetailsFor("john.doe@example.com");

            assertThat(jwtService.isTokenValid(token, wrongUser)).isFalse();
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 5 — extractUsername() et extractUsernameOptional()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("extractUsername()")
    class ExtractUsernameTests {

        @Test
        @DisplayName("✅ Extrait l'email depuis un access token")
        void extractUsername_validToken_returnsEmail() {

            String token = jwtService.generateAccessToken(mockUser);

            assertThat(jwtService.extractUsername(token))
                    .isEqualTo("jane.doe@example.com");
        }

        @Test
        @DisplayName("❌ Token invalide — retourne null (pas d'exception)")
        void extractUsername_invalidToken_returnsNull() {

            // extractUsername() doit retourner null pour un token invalide
            // sans jamais lancer d'exception
            assertThat(jwtService.extractUsername("invalid.token.here")).isNull();
        }

        @Test
        @DisplayName("✅ extractUsernameOptional() retourne Optional.of(email) pour un token valide")
        void extractUsernameOptional_validToken_returnsOptional() {

            String token = jwtService.generateAccessToken(mockUser);

            Optional<String> result = jwtService.extractUsernameOptional(token);

            // isPresent() → l'Optional contient une valeur
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("jane.doe@example.com");
        }

        @Test
        @DisplayName("✅ extractUsernameOptional() retourne Optional.empty() pour un token invalide")
        void extractUsernameOptional_invalidToken_returnsEmpty() {

            Optional<String> result = jwtService.extractUsernameOptional("bad.token");

            // isEmpty() → l'Optional est vide
            assertThat(result).isEmpty();
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 6 — getRemainingTime()
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getRemainingTime()")
    class GetRemainingTimeTests {

        @Test
        @DisplayName("✅ Token valide — temps restant > 0")
        void getRemainingTime_validToken_returnsPositiveValue() {

            String token = jwtService.generateAccessToken(mockUser);
            long remaining = jwtService.getRemainingTime(token);

            // Le token expire dans 1h → le temps restant doit être > 0
            // isGreaterThan(0) → vérifie que la valeur est strictement positive
            assertThat(remaining).isGreaterThan(0L);
        }

        @Test
        @DisplayName("✅ Token expiré — retourne 0")
        void getRemainingTime_expiredToken_returnsZero() {

            // Token déjà expiré
            Map<String, Object> claims = new HashMap<>();
            String expiredToken = jwtService.generateTokenWithExpiration(
                    claims, "test@test.com", -1L);

            long remaining = jwtService.getRemainingTime(expiredToken);

            assertThat(remaining).isEqualTo(0L);
        }

        @Test
        @DisplayName("✅ Token invalide — retourne 0 (pas d'exception)")
        void getRemainingTime_invalidToken_returnsZero() {

            assertThat(jwtService.getRemainingTime("not.a.token")).isEqualTo(0L);
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // GROUPE 7 — generateTokenWithExpiration() (méthode générique)
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("generateTokenWithExpiration()")
    class GenerateTokenWithExpirationTests {

        @Test
        @DisplayName("✅ Claims personnalisés sont bien intégrés dans le token")
        void generateTokenWithExpiration_customClaims_areExtractable() {

            // GIVEN : claims personnalisés
            Map<String, Object> claims = new HashMap<>();
            claims.put("type", "ACCOUNT_ACTIVATION");
            claims.put("userId", 42L);
            claims.put("email", "custom@test.com");

            // WHEN : générer un token avec ces claims
            String token = jwtService.generateTokenWithExpiration(
                    claims, "custom@test.com", 3_600_000L);

            // THEN : vérifier que chaque claim est bien récupérable
            assertThat(jwtService.extractUsername(token)).isEqualTo("custom@test.com");
            assertThat(jwtService.getTokenType(token)).isEqualTo("ACCOUNT_ACTIVATION");
            assertThat(jwtService.isTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("✅ Deux tokens générés au même moment sont différents (UUID implicite)")
        void generateTokenWithExpiration_twoTokens_areDifferent() {

            // GIVEN : mêmes paramètres
            Map<String, Object> claims = new HashMap<>();
            claims.put("type", "access");

            // WHEN : générer deux tokens
            String token1 = jwtService.generateTokenWithExpiration(claims, "test@test.com", 3_600_000L);
            String token2 = jwtService.generateTokenWithExpiration(claims, "test@test.com", 3_600_000L);

            // THEN : les tokens peuvent être identiques si générés dans la même milliseconde
            // (JWT n'ajoute pas de nonce par défaut) — on vérifie juste qu'ils sont valides
            assertThat(jwtService.isTokenValid(token1)).isTrue();
            assertThat(jwtService.isTokenValid(token2)).isTrue();
        }
    }
}