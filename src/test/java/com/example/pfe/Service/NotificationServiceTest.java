package com.example.pfe.Service;

import com.example.pfe.Repository.NotificationRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.NotificationDTO;
import com.example.pfe.entities.Notification;
import com.example.pfe.entities.User;
import com.example.pfe.enums.NotificationType;
import com.example.pfe.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService - Tests Unitaires")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository         userRepository;
    @Mock private SseEmitterService sseEmitterService;

    @InjectMocks
    private NotificationService notificationService;

    // ── Fixture ───────────────────────────────────────────────────────────────

    private User buildUser(Long id, String firstName) {
        User u = new User();
        u.setId(id);
        u.setFirstName(firstName);
        u.setLastName("Doe");
        u.setEmail("user@test.com");
        return u;
    }

    /** Stub userRepository + notificationRepository.save for a happy-path notify call. */
    private void stubHappyPath(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. USER NOT FOUND — guard for ALL notifyXXX methods
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Utilisateur introuvable → ResourceNotFoundException")
    class UserNotFound {

        @Test
        @DisplayName("notifyWelcome lève ResourceNotFoundException si user inconnu")
        void shouldThrowOnWelcomeWhenUserNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.notifyWelcome(1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("1");
        }

        @Test
        @DisplayName("notifyLateArrival lève ResourceNotFoundException si user inconnu")
        void shouldThrowOnLateArrivalWhenUserNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.notifyLateArrival(1L, "09:15"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("notifyLeaveApproved lève ResourceNotFoundException si user inconnu")
        void shouldThrowOnLeaveApprovedWhenUserNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.notifyLeaveApproved(1L, "Annual", "01 May 2026", "05 May 2026"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. SAVE + SSE PUSH — core behaviour
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Notification sauvegardée et poussée via SSE")
    class SaveAndPush {

        @Test
        @DisplayName("notifyWelcome sauvegarde la notification et push SSE")
        void shouldSaveAndPushOnWelcome() {
            User user = buildUser(1L, "Oumayma");
            stubHappyPath(user);

            notificationService.notifyWelcome(1L);

            verify(notificationRepository).save(any(Notification.class));
            verify(sseEmitterService).push(eq(1L), any(NotificationDTO.class));
        }

        @Test
        @DisplayName("notifyLateArrival sauvegarde et push avec le bon type")
        void shouldSaveWithCorrectTypeOnLateArrival() {
            User user = buildUser(1L, "Alice");
            stubHappyPath(user);

            notificationService.notifyLateArrival(1L, "09:05");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(NotificationType.LATE_ARRIVAL);
            assertThat(captor.getValue().getMessage()).contains("09:05");
        }

        @Test
        @DisplayName("notifyMissedCheckout sauvegarde avec le bon type et la date")
        void shouldSaveWithCorrectTypeOnMissedCheckout() {
            User user = buildUser(1L, "Bob");
            stubHappyPath(user);

            notificationService.notifyMissedCheckout(1L, "11 Apr 2026");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(NotificationType.MISSED_CHECKOUT);
            assertThat(captor.getValue().getMessage()).contains("11 Apr 2026");
        }

        @Test
        @DisplayName("notifyEarlyDeparture sauvegarde avec le bon type et l'heure")
        void shouldSaveWithCorrectTypeOnEarlyDeparture() {
            User user = buildUser(1L, "Carol");
            stubHappyPath(user);

            notificationService.notifyEarlyDeparture(1L, "16:30");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(NotificationType.EARLY_DEPARTURE);
            assertThat(captor.getValue().getMessage()).contains("16:30");
        }

        @Test
        @DisplayName("notifyLeaveApproved sauvegarde avec les bonnes dates")
        void shouldSaveWithDatesOnLeaveApproved() {
            User user = buildUser(1L, "Dave");
            stubHappyPath(user);

            notificationService.notifyLeaveApproved(1L, "Annual", "01 May 2026", "05 May 2026");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(NotificationType.LEAVE_APPROVED);
            assertThat(captor.getValue().getMessage())
                    .contains("Annual")
                    .contains("01 May 2026")
                    .contains("05 May 2026");
        }

        @Test
        @DisplayName("notifyLeaveRejected sauvegarde avec la raison")
        void shouldSaveWithReasonOnLeaveRejected() {
            User user = buildUser(1L, "Eve");
            stubHappyPath(user);

            notificationService.notifyLeaveRejected(1L, "Sick", "Not enough coverage");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(NotificationType.LEAVE_REJECTED);
            assertThat(captor.getValue().getMessage()).contains("Not enough coverage");
        }

        @Test
        @DisplayName("notifyLeaveRejected sans raison ne plante pas")
        void shouldHandleNullReasonOnLeaveRejected() {
            User user = buildUser(1L, "Eve");
            stubHappyPath(user);

            assertThatCode(() -> notificationService.notifyLeaveRejected(1L, "Annual", null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("notifyProjectAssigned sauvegarde avec le nom du projet")
        void shouldSaveWithProjectNameOnAssigned() {
            User user = buildUser(1L, "Frank");
            stubHappyPath(user);

            notificationService.notifyProjectAssigned(1L, "Phoenix");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(NotificationType.PROJECT_ASSIGNED);
            assertThat(captor.getValue().getMessage()).contains("Phoenix");
        }

        @Test
        @DisplayName("notifyPmAssigned sauvegarde avec le nom du PM et du projet")
        void shouldSaveWithPmAndProjectOnPmAssigned() {
            User user = buildUser(1L, "Grace");
            stubHappyPath(user);

            notificationService.notifyPmAssigned(1L, "Alice Smith", "Phoenix");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(NotificationType.PM_ASSIGNED);
            assertThat(captor.getValue().getMessage())
                    .contains("Alice Smith")
                    .contains("Phoenix");
        }

        @Test
        @DisplayName("notifyProjectUpdated sauvegarde avec le nom du projet")
        void shouldSaveWithProjectNameOnUpdated() {
            User user = buildUser(1L, "Henry");
            stubHappyPath(user);

            notificationService.notifyProjectUpdated(1L, "Phoenix");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(NotificationType.PROJECT_UPDATED);
            assertThat(captor.getValue().getMessage()).contains("Phoenix");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. toDTO mapping
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Mapping Notification → NotificationDTO")
    class ToDtoMapping {

        @Test
        @DisplayName("toDTO mappe correctement tous les champs")
        void shouldMapAllFieldsCorrectly() {
            Notification notification = Notification.builder()
                    .id(10L)
                    .type(NotificationType.WELCOME)
                    .title("Hello")
                    .message("Welcome aboard!")
                    .link("/dashboard")
                    .read(false)
                    .build();

            NotificationDTO dto = ReflectionTestUtils
                    .invokeMethod(notificationService, "toDTO", notification);

            assertThat(dto).isNotNull();
            assertThat(dto.getId()).isEqualTo(10L);
            assertThat(dto.getType()).isEqualTo(NotificationType.WELCOME);
            assertThat(dto.getTitle()).isEqualTo("Hello");
            assertThat(dto.getMessage()).isEqualTo("Welcome aboard!");
            assertThat(dto.getLink()).isEqualTo("/dashboard");
            assertThat(dto.isRead()).isFalse();
        }

        @Test
        @DisplayName("toDTO sur une notification lue retourne read=true")
        void shouldMapReadFlag() {
            Notification notification = Notification.builder()
                    .id(5L)
                    .type(NotificationType.LATE_ARRIVAL)
                    .title("Late")
                    .message("You were late")
                    .link("/attendance")
                    .read(true)
                    .build();

            NotificationDTO dto = ReflectionTestUtils
                    .invokeMethod(notificationService, "toDTO", notification);

            assertThat(dto.isRead()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. getForUser
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getForUser()")
    class GetForUser {

        @Test
        @DisplayName("Retourne une page de NotificationDTO")
        void shouldReturnPageOfDTOs() {
            Notification n = Notification.builder()
                    .id(1L).type(NotificationType.WELCOME)
                    .title("T").message("M").link("/").read(false).build();
            Page<Notification> page = new PageImpl<>(List.of(n));
            Pageable pageable = PageRequest.of(0, 10);

            when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), eq(pageable)))
                    .thenReturn(page);

            Page<NotificationDTO> result = notificationService.getForUser(1L, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("T");
        }

        @Test
        @DisplayName("Retourne une page vide si aucune notification")
        void shouldReturnEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), eq(pageable)))
                    .thenReturn(Page.empty());

            Page<NotificationDTO> result = notificationService.getForUser(1L, pageable);

            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. getUnreadCount
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getUnreadCount()")
    class GetUnreadCount {

        @Test
        @DisplayName("Retourne le nombre de notifications non lues")
        void shouldReturnUnreadCount() {
            when(notificationRepository.countByUserIdAndReadFalse(1L)).thenReturn(5L);

            long count = notificationService.getUnreadCount(1L);

            assertThat(count).isEqualTo(5L);
        }

        @Test
        @DisplayName("Retourne 0 si toutes les notifications sont lues")
        void shouldReturnZeroWhenAllRead() {
            when(notificationRepository.countByUserIdAndReadFalse(1L)).thenReturn(0L);

            assertThat(notificationService.getUnreadCount(1L)).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. markRead
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("markRead()")
    class MarkRead {

        @Test
        @DisplayName("Délègue au repository avec les bons IDs")
        void shouldMarkNotificationRead() {
            notificationService.markRead(10L, 1L);

            verify(notificationRepository).markReadById(10L, 1L);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. markAllRead
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("markAllRead()")
    class MarkAllRead {

        @Test
        @DisplayName("Délègue au repository avec le bon userId")
        void shouldMarkAllNotificationsRead() {
            notificationService.markAllRead(1L);

            verify(notificationRepository).markAllReadByUserId(1L);
        }
    }
}