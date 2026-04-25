package com.example.pfe.Controller;

import com.example.pfe.Service.UserService;
import com.example.pfe.Service.JwtService;
import com.example.pfe.dto.UserRequestDTO;
import com.example.pfe.dto.UserResponseDTO;
import com.example.pfe.dto.UserStatsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@ContextConfiguration(classes = {
        UserController.class,
        UserControllerTest.TestSecurityConfig.class
})
@DisplayName("UserController - Tests")
class UserControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean JwtService  jwtService;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(
                                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
            return http.build();
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private UserResponseDTO buildResponse() {
        return UserResponseDTO.builder()
                .id(1L)
                .email("user@test.com")
                .firstName("John")
                .lastName("Doe")
                .build();
    }

    private UserRequestDTO buildRequest() {
        return UserRequestDTO.builder()
                .firstName("John")
                .lastName("Doe")
                .birthDate(java.time.LocalDate.of(1990, 1, 1))
                .gender(com.example.pfe.enums.Gender.MALE)
                .nationalId("12345678")
                .nationality("Tunisian")
                .maritalStatus(com.example.pfe.enums.MaritalStatus.SINGLE)
                .email("user@test.com")
                .phone("12345678")
                .department(com.example.pfe.enums.Department.IT)
                .hireDate(java.time.LocalDate.of(2023, 1, 1))
                .contractType(com.example.pfe.enums.ContractType.CDI)
                .baseSalary(2000.0)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/users
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/users")
    class CreateUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can create user → 200 OK")
        void shouldCreateUserForAdmin() throws Exception {
            when(userService.createUser(any())).thenReturn(buildResponse());

            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("user@test.com"));

            verify(userService).createUser(any());
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("GENERAL_MANAGER can create user → 200 OK")
        void shouldCreateUserForGM() throws Exception {
            when(userService.createUser(any())).thenReturn(buildResponse());

            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/users/{id}
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/users/{id}")
    class GetUserById {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can get user by ID → 200 OK")
        void shouldReturnUserForAdmin() throws Exception {
            when(userService.getUserById(1L)).thenReturn(buildResponse());

            mockMvc.perform(get("/api/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("user@test.com"));
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(get("/api/users/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/users/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PUT /api/users/{id}
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("PUT /api/users/{id}")
    class UpdateUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can update user → 200 OK")
        void shouldUpdateUserForAdmin() throws Exception {
            when(userService.updateUser(eq(1L), any())).thenReturn(buildResponse());

            mockMvc.perform(put("/api/users/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));

            verify(userService).updateUser(eq(1L), any());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(put("/api/users/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(put("/api/users/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/users/{id}/reset-password
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/users/{id}/reset-password")
    class ResetPassword {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can reset password → 200 OK")
        void shouldResetPasswordForAdmin() throws Exception {
            doNothing().when(userService).resetUserPassword(1L);

            mockMvc.perform(post("/api/users/1/reset-password"))
                    .andExpect(status().isOk());

            verify(userService).resetUserPassword(1L);
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(post("/api/users/1/reset-password"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/users/1/reset-password"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/users/{id}/reactivate
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/users/{id}/reactivate")
    class ReactivateUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can reactivate user → 200 OK")
        void shouldReactivateUserForAdmin() throws Exception {
            doNothing().when(userService).reactivateUser(1L);

            mockMvc.perform(post("/api/users/1/reactivate"))
                    .andExpect(status().isOk());

            verify(userService).reactivateUser(1L);
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(post("/api/users/1/reactivate"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/users/1/reactivate"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/users/stats
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/users/stats")
    class GetUserStats {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can get stats → 200 OK")
        void shouldReturnStatsForAdmin() throws Exception {
            UserStatsDTO stats = UserStatsDTO.builder()
                    .total(10)
                    .active(8)
                    .pending(1)
                    .disabled(1)
                    .locked(0)
                    .build();
            when(userService.getUserStats()).thenReturn(stats);

            mockMvc.perform(get("/api/users/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(10))    // was $.totalUsers
                    .andExpect(jsonPath("$.active").value(8));   // was $.activeUsers
        }

        @Test
        @WithMockUser(roles = "HR_MANAGER")
        @DisplayName("HR_MANAGER can get stats → 200 OK")
        void shouldReturnStatsForHR() throws Exception {
            when(userService.getUserStats()).thenReturn(UserStatsDTO.builder().build());

            mockMvc.perform(get("/api/users/stats"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(get("/api/users/stats"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/users/stats"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/users/{id}/enable  &  disable
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/users/{id}/enable and /disable")
    class EnableDisableUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can enable user → 200 OK")
        void shouldEnableUser() throws Exception {
            doNothing().when(userService).enableUser(1L);

            mockMvc.perform(post("/api/users/1/enable"))
                    .andExpect(status().isOk());

            verify(userService).enableUser(1L);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can disable user → 200 OK")
        void shouldDisableUser() throws Exception {
            doNothing().when(userService).disableUser(1L);

            mockMvc.perform(post("/api/users/1/disable"))
                    .andExpect(status().isOk());

            verify(userService).disableUser(1L);
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied on enable → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(post("/api/users/1/enable"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/users/1/enable"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/users/{id}/approve  &  reject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/users/{id}/approve and /reject")
    class ApproveRejectUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can approve user → 200 OK")
        void shouldApproveUser() throws Exception {
            doNothing().when(userService).approveUser(1L);

            mockMvc.perform(post("/api/users/1/approve"))
                    .andExpect(status().isOk());

            verify(userService).approveUser(1L);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can reject user → 200 OK")
        void shouldRejectUser() throws Exception {
            doNothing().when(userService).rejectUser(1L);

            mockMvc.perform(post("/api/users/1/reject"))
                    .andExpect(status().isOk());

            verify(userService).rejectUser(1L);
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(post("/api/users/1/approve"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/users/1/approve"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/users
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/users")
    class GetAllUsers {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can get all users (no search) → 200 OK")
        void shouldReturnAllUsers() throws Exception {
            Page<UserResponseDTO> page = new PageImpl<>(
                    List.of(buildResponse()),
                    PageRequest.of(0, 10),
                    1
            );
            when(userService.getAllUsers(any())).thenReturn(page);

            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].email").value("user@test.com"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can search users → 200 OK")
        void shouldReturnSearchResults() throws Exception {
            Page<UserResponseDTO> page = new PageImpl<>(List.of(buildResponse()));
            when(userService.searchUsers(eq("john"), any())).thenReturn(page);

            mockMvc.perform(get("/api/users").param("search", "john"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));

            verify(userService).searchUsers(eq("john"), any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Blank search param falls back to getAllUsers")
        void shouldFallbackToGetAllOnBlankSearch() throws Exception {
            Page<UserResponseDTO> page = new PageImpl<>(List.of());
            when(userService.getAllUsers(any())).thenReturn(page);

            mockMvc.perform(get("/api/users").param("search", "   "))
                    .andExpect(status().isOk());

            verify(userService).getAllUsers(any());
            verify(userService, never()).searchUsers(any(), any());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/users/{id}/photo
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/users/{id}/photo")
    class UploadPhoto {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can upload photo → 200 OK")
        void shouldUploadPhotoForAdmin() throws Exception {
            when(userService.uploadUserPhoto(eq(1L), any()))
                    .thenReturn("https://example.com/avatar.jpg");

            MockMultipartFile photo = new MockMultipartFile(
                    "photo", "avatar.jpg",
                    MediaType.IMAGE_JPEG_VALUE,
                    "fake-image-bytes".getBytes()
            );

            mockMvc.perform(multipart("/api/users/1/photo").file(photo))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.jpg"))
                    .andExpect(jsonPath("$.message").value("Photo uploaded successfully"));
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            MockMultipartFile photo = new MockMultipartFile(
                    "photo", "avatar.jpg",
                    MediaType.IMAGE_JPEG_VALUE,
                    "fake-image-bytes".getBytes()
            );

            mockMvc.perform(multipart("/api/users/1/photo").file(photo))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            MockMultipartFile photo = new MockMultipartFile(
                    "photo", "avatar.jpg",
                    MediaType.IMAGE_JPEG_VALUE,
                    "fake-image-bytes".getBytes()
            );

            mockMvc.perform(multipart("/api/users/1/photo").file(photo))
                    .andExpect(status().isUnauthorized());
        }
    }
}