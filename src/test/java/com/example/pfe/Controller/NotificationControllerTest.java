package com.example.pfe.Controller;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.NotificationService;
import com.example.pfe.Service.SseEmitterService;
import com.example.pfe.dto.NotificationDTO;
import com.example.pfe.entities.User;
import com.example.pfe.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@ContextConfiguration(classes = {
        NotificationController.class,
        NotificationControllerTest.TestSecurityConfig.class,
        NotificationControllerTest.GlobalExceptionHandler.class

})
@DisplayName("NotificationController - Tests")
class NotificationControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @MockBean NotificationService notificationService;
    @MockBean SseEmitterService   sseEmitterService;
    @MockBean UserRepository      userRepository;


    @RestControllerAdvice
    static  class GlobalExceptionHandler {

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        }
    }
    // ── Security config minimale ──────────────────────────────────────────────
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
                                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                            )
                    );
            return http.build();
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static final String EMAIL  = "john@test.com";
    private static final Long   USER_ID = 1L;

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private NotificationDTO buildNotificationDTO(Long id, String title) {
        return NotificationDTO.builder()
                .id(id)
                .title(title)
                .message("message")
                .read(false)
                .build();
    }

    @BeforeEach
    void stubUserResolution() {
        // La plupart des tests ont besoin que l'email → userId soit résolu
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(buildUser(USER_ID, EMAIL)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/notifications/stream
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/notifications/stream")
    class Stream {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Crée un SseEmitter pour l'utilisateur authentifié → 200 OK")
        void shouldCreateSseEmitter() throws Exception {
            when(sseEmitterService.createEmitter(USER_ID)).thenReturn(new SseEmitter());

            mockMvc.perform(get("/api/notifications/stream")
                            .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                    .andExpect(status().isOk());

            verify(sseEmitterService).createEmitter(USER_ID);
        }

        @Test
        @DisplayName("Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/notifications/stream"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/notifications
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/notifications")
    class GetNotifications {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Retourne la page de notifications → 200 OK avec contenu")
        void shouldReturnNotificationPage() throws Exception {
            NotificationDTO dto = buildNotificationDTO(10L, "Test notif");
            Page<NotificationDTO> page = new PageImpl<>(List.of(dto));

            when(notificationService.getForUser(eq(USER_ID), any(PageRequest.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/notifications")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Test notif"));
        }

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Utilise les valeurs par défaut page=0, size=20")
        void shouldUseDefaultPagination() throws Exception {
            when(notificationService.getForUser(eq(USER_ID), any()))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/notifications"))
                    .andExpect(status().isOk());

            verify(notificationService).getForUser(eq(USER_ID), eq(PageRequest.of(0, 20)));
        }

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Retourne une page vide si aucune notification")
        void shouldReturnEmptyPage() throws Exception {
            when(notificationService.getForUser(eq(USER_ID), any()))
                    .thenReturn(Page.empty());

            mockMvc.perform(get("/api/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/notifications"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/notifications/unread/count
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/notifications/unread/count")
    class GetUnreadCount {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Retourne le nombre de notifications non lues → 200 OK")
        void shouldReturnUnreadCount() throws Exception {
            when(notificationService.getUnreadCount(USER_ID)).thenReturn(5L);

            mockMvc.perform(get("/api/notifications/unread/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(5));
        }

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Retourne 0 si toutes les notifications sont lues")
        void shouldReturnZeroWhenAllRead() throws Exception {
            when(notificationService.getUnreadCount(USER_ID)).thenReturn(0L);

            mockMvc.perform(get("/api/notifications/unread/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(0));
        }

        @Test
        @DisplayName("Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/notifications/unread/count"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PATCH /api/notifications/{id}/read
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("PATCH /api/notifications/{id}/read")
    class MarkRead {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Marque une notification comme lue → 204 No Content")
        void shouldMarkNotificationRead() throws Exception {
            mockMvc.perform(patch("/api/notifications/10/read"))
                    .andExpect(status().isNoContent());

            verify(notificationService).markRead(10L, USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Transmet le bon notificationId au service")
        void shouldPassCorrectNotificationId() throws Exception {
            mockMvc.perform(patch("/api/notifications/42/read"))
                    .andExpect(status().isNoContent());

            verify(notificationService).markRead(42L, USER_ID);
        }

        @Test
        @DisplayName("Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(patch("/api/notifications/10/read"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PATCH /api/notifications/read-all
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("PATCH /api/notifications/read-all")
    class MarkAllRead {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Marque toutes les notifications comme lues → 204 No Content")
        void shouldMarkAllNotificationsRead() throws Exception {
            mockMvc.perform(patch("/api/notifications/read-all"))
                    .andExpect(status().isNoContent());

            verify(notificationService).markAllRead(USER_ID);
        }

        @Test
        @DisplayName("Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(patch("/api/notifications/read-all"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // resolveUserId — user not found
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("resolveUserId — utilisateur introuvable")
    class ResolveUserIdNotFound {

        @Test
        @WithMockUser(username = "ghost@test.com")
        @DisplayName("Lève 404 si l'email authentifié ne correspond à aucun utilisateur")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/notifications"))
                    .andExpect(status().isNotFound());
        }
    }
}