package com.example.pfe.Controller;
import com.example.pfe.dto.ChangePasswordDTO;

import com.example.pfe.Controller.AuthController;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.AuthenticationService;
import com.example.pfe.Service.JwtService;
import com.example.pfe.Service.TokenBlacklistService;
import com.example.pfe.Service.UserService;
import com.example.pfe.dto.*;
import com.example.pfe.entities.User;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = {
        AuthController.class,
        AuthControllerTest.TestSecurityConfig.class
})
@DisplayName("AuthController - Tests")
class AuthControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthenticationService  authenticationService;
    @MockBean TokenBlacklistService  blacklistService;
    @MockBean JwtService             jwtService;
    @MockBean UserRepository         userRepository;
    @MockBean UserMapper             userMapper;
    @MockBean UserService            userService;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(
                                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
            return http.build();
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static final String USER_EMAIL = "user@test.com";

    private LoginRequestDTO buildLoginRequest() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setEmail(USER_EMAIL);
        req.setPassword("password123");
        return req;
    }

    private RegisterRequestDTO buildRegisterRequest() {
        return RegisterRequestDTO.builder()
                .email(USER_EMAIL)
                .firstName("John")
                .lastName("Doe")
                .birthDate(java.time.LocalDate.of(1990, 1, 1))
                .gender(com.example.pfe.enums.Gender.MALE)
                .nationalId("12345678")
                .nationality("Tunisian")
                .maritalStatus(com.example.pfe.enums.MaritalStatus.SINGLE)
                .phone("12345678")
                .department(com.example.pfe.enums.Department.IT)
                .hireDate(java.time.LocalDate.of(2023, 1, 1))
                .contractType(com.example.pfe.enums.ContractType.CDI)
                .build();
    }

    private JwtResponseDTO buildJwtResponse() {
        return JwtResponseDTO.builder()
                .token("mock.jwt.token")
                .build();
    }

    private RegistrationResponseDTO buildRegistrationResponse() {
        return RegistrationResponseDTO.builder()
                .userId(1L)       // was id → userId
                .email(USER_EMAIL)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/auth/login
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("Valid credentials → 200 OK with JWT token")
        void shouldReturnTokenOnValidCredentials() throws Exception {
            when(authenticationService.authenticate(any())).thenReturn(buildJwtResponse());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildLoginRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("mock.jwt.token"));
        }

        @Test
        @DisplayName("Invalid body (missing fields) → 400 Bad Request")
        void shouldReturn400WhenBodyInvalid() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/auth/register
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("Valid registration → 201 Created")
        void shouldReturn201OnSuccess() throws Exception {
            when(authenticationService.register(any())).thenReturn(buildRegistrationResponse());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRegisterRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value(USER_EMAIL));
        }

        @Test
        @DisplayName("Duplicate email (BusinessException) → 400 Bad Request")
        void shouldReturn400WhenEmailExists() throws Exception {
            when(authenticationService.register(any()))
                    .thenThrow(new BusinessException("Email already exists"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRegisterRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Email already exists"));
        }

        @Test
        @DisplayName("Unexpected error → 500 Internal Server Error")
        void shouldReturn500OnUnexpectedError() throws Exception {
            when(authenticationService.register(any()))
                    .thenThrow(new RuntimeException("DB down"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRegisterRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Registration failed. Please try again."));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/auth/pending-registrations
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/auth/pending-registrations")
    class PendingRegistrations {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can get pending registrations → 200 OK")
        void shouldReturnPendingForAdmin() throws Exception {
            when(authenticationService.getPendingRegistrations())
                    .thenReturn(java.util.List.of(buildRegistrationResponse()));

            mockMvc.perform(get("/api/auth/pending-registrations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("GENERAL_MANAGER can get pending registrations → 200 OK")
        void shouldReturnPendingForGM() throws Exception {
            when(authenticationService.getPendingRegistrations())
                    .thenReturn(java.util.List.of());

            mockMvc.perform(get("/api/auth/pending-registrations"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(get("/api/auth/pending-registrations"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/auth/pending-registrations"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/auth/approve-registration/{userId}
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/auth/approve-registration/{userId}")
    class ApproveRegistration {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can approve → 200 OK")
        void shouldApproveForAdmin() throws Exception {
            when(authenticationService.approveRegistration(1L))
                    .thenReturn(buildRegistrationResponse());

            mockMvc.perform(post("/api/auth/approve-registration/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(1));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("User not found → 404 Not Found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(authenticationService.approveRegistration(99L))
                    .thenThrow(new ResourceNotFoundException("User not found"));

            mockMvc.perform(post("/api/auth/approve-registration/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("User not found"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("BusinessException → 400 Bad Request")
        void shouldReturn400OnBusinessException() throws Exception {
            when(authenticationService.approveRegistration(1L))
                    .thenThrow(new BusinessException("Already approved"));

            mockMvc.perform(post("/api/auth/approve-registration/1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Already approved"));
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(post("/api/auth/approve-registration/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/auth/approve-registration/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/auth/me
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/auth/me")
    class GetCurrentUser {

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Authenticated user gets their profile → 200 OK")
        void shouldReturnCurrentUser() throws Exception {
            User mockUser = mock(User.class);
            UserResponseDTO responseDTO = UserResponseDTO.builder()
                    .email(USER_EMAIL)
                    .build();

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
            when(userMapper.toResponseDTO(mockUser)).thenReturn(responseDTO);

            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(USER_EMAIL));
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/auth/logout
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        @Test
        @WithMockUser
        @DisplayName("Authenticated user can logout → 200 OK")
        void shouldLogoutSuccessfully() throws Exception {
            doNothing().when(blacklistService).blacklist(anyString());

            mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", "Bearer mock.jwt.token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").exists());

            verify(blacklistService).blacklist("mock.jwt.token");
        }

        @Test
        @DisplayName("Unauthenticated (no security context) → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            // Must send the header — without it Spring returns 400 on missing
            // @RequestHeader before security even runs
            mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", "Bearer some.token"))
                    .andExpect(status().isUnauthorized());
        }
    }
    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/auth/activate
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/auth/activate")
    class ActivateAccount {

        @Test
        @DisplayName("Valid activation request → 200 OK with JWT")
        void shouldActivateAccount() throws Exception {
            ActivationRequestDTO req = new ActivationRequestDTO();
            req.setToken("activation-token");
            req.setUsername("johndoe");          // was setPassword → setUsername
            req.setNewPassword("NewPass@123");   // was setPassword → setNewPassword
            req.setConfirmPassword("NewPass@123");

            when(authenticationService.activateAccount(any())).thenReturn(buildJwtResponse());

            mockMvc.perform(post("/api/auth/activate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("mock.jwt.token"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/auth/validate-activation-token/{token}
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/auth/validate-activation-token/{token}")
    class ValidateActivationToken {

        @Test
        @DisplayName("Valid token → 200 OK with user info")
        void shouldReturnUserInfoForValidToken() throws Exception {
            User mockUser = mock(User.class);
            when(mockUser.getEmail()).thenReturn(USER_EMAIL);
            when(mockUser.getFirstName()).thenReturn("John");
            when(mockUser.getLastName()).thenReturn("Doe");
            when(mockUser.getId()).thenReturn(1L);

            when(authenticationService.validateActivationTokenApi("valid-token")).thenReturn(true);
            when(userRepository.findByActivationToken("valid-token")).thenReturn(Optional.of(mockUser));

            mockMvc.perform(get("/api/auth/validate-activation-token/valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.email").value(USER_EMAIL));
        }

        @Test
        @DisplayName("Invalid token → 400 Bad Request")
        void shouldReturn400ForInvalidToken() throws Exception {
            when(authenticationService.validateActivationTokenApi("bad-token")).thenReturn(false);

            mockMvc.perform(get("/api/auth/validate-activation-token/bad-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.valid").value(false));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/auth/resend-activation
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/auth/resend-activation")
    class ResendActivation {

        @Test
        @DisplayName("Valid email → 200 OK")
        void shouldResendActivationEmail() throws Exception {
            when(authenticationService.resendActivationEmail(USER_EMAIL))
                    .thenReturn(new ResendActivationResponseDTO());

            mockMvc.perform(post("/api/auth/resend-activation")
                            .param("email", USER_EMAIL))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Unknown email → 400 Bad Request")
        void shouldReturn400ForUnknownEmail() throws Exception {
            when(authenticationService.resendActivationEmail("unknown@test.com"))
                    .thenThrow(new RuntimeException("Email not found"));

            mockMvc.perform(post("/api/auth/resend-activation")
                            .param("email", "unknown@test.com"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Email not found"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/auth/change-password
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/auth/change-password")
    class ChangePassword {

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Valid request → 200 OK")
        void shouldChangePasswordSuccessfully() throws Exception {
            String body = """
            {
              "currentPassword": "oldPass@1",
              "newPassword":     "newPass@1",
              "confirmPassword": "newPass@1"
            }
            """;

            doNothing().when(userService).changePassword(eq(USER_EMAIL), any());

            mockMvc.perform(post("/api/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Password changed successfully"));
        }

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Wrong current password (BusinessException) → 400 Bad Request")
        void shouldReturn400OnWrongPassword() throws Exception {
            String body = """
            {
              "currentPassword": "wrongPass@1",
              "newPassword":     "newPass@1",
              "confirmPassword": "newPass@1"
            }
            """;

            doThrow(new BusinessException("Current password is incorrect"))
                    .when(userService).changePassword(eq(USER_EMAIL), any());

            mockMvc.perform(post("/api/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        }
    }
}