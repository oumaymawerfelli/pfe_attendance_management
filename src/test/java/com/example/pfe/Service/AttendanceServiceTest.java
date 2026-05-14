package com.example.pfe.Service;

import com.example.pfe.Repository.AttendanceRepository;
import com.example.pfe.Repository.LeaveRequestRepository;
import com.example.pfe.Repository.TeamAssignmentRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.AttendanceFilterDTO;
import com.example.pfe.dto.AttendanceResponseDTO;
import com.example.pfe.entities.Attendance;
import com.example.pfe.entities.AttendanceConfig;
import com.example.pfe.entities.TeamAssignment;
import com.example.pfe.entities.User;
import com.example.pfe.enums.AttendanceStatus;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.AttendanceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceService - Tests Unitaires")
class AttendanceServiceTest {

    @Mock private AttendanceRepository     attendanceRepository;
    @Mock private LeaveRequestRepository   leaveRequestRepository;
    @Mock private UserRepository           userRepository;
    @Mock private TeamAssignmentRepository teamAssignmentRepository;
    @Mock private AttendanceMapper         attendanceMapper;
    @Mock private AttendanceConfigService configService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AttendanceService attendanceService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private User buildUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setFirstName("John");
        u.setLastName("Doe");
        u.setEmail("john@test.com");
        return u;
    }

    private Attendance buildAttendance(Long userId, LocalDate date, LocalDateTime checkIn) {
        User user = buildUser(userId);
        return Attendance.builder()
                .user(user)
                .date(date)
                .checkIn(checkIn)
                .status(AttendanceStatus.PRESENT)
                .overtimeHours(0.0)
                .build();
    }

    private AttendanceFilterDTO buildFilter(Integer month, Integer year) {
        AttendanceFilterDTO f = new AttendanceFilterDTO();
        f.setMonth(month);
        f.setYear(year);
        return f;
    }

    /** Stub the 3 config keys needed by computeStatus */
    private void stubStatusConfig(int lateHour, int lateMinute) {
        when(configService.getInt(AttendanceConfig.KEY_LATE_HOUR)).thenReturn(lateHour);
        when(configService.getInt(AttendanceConfig.KEY_LATE_MINUTE)).thenReturn(lateMinute);
    }

    /** Stub the config keys needed by computeDuration */
    private void stubDurationConfig(double standardHours, int halfDayThreshold, int earlyDepartureHour) {
        when(configService.getDouble(AttendanceConfig.KEY_STANDARD_HOURS)).thenReturn(standardHours);
        when(configService.getInt(AttendanceConfig.KEY_HALF_DAY_THRESHOLD)).thenReturn(halfDayThreshold);
        when(configService.getInt(AttendanceConfig.KEY_EARLY_DEPARTURE_HOUR)).thenReturn(earlyDepartureHour);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // checkIn
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("checkIn()")
    class CheckIn {

        @Test
        @DisplayName("Enregistre un check-in PRESENT dans la plage horaire valide")
        void shouldRecordPresentCheckIn() {
            Long userId = 1L;
            User user   = buildUser(userId);

            // Heure actuelle simulée indirectement via configService
            when(configService.getInt(AttendanceConfig.KEY_VALID_CHECKIN_START)).thenReturn(6);
            when(attendanceRepository.existsByUserIdAndDate(eq(userId), any())).thenReturn(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            stubStatusConfig(8, 30); // late after 08:30
            when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            attendanceService.checkIn(userId);

            verify(attendanceRepository).save(any(Attendance.class));
        }

        @Test
        @DisplayName("Ne fait rien si l'heure est avant KEY_VALID_CHECKIN_START")
        void shouldSkipWhenTooEarly() {
            // KEY_VALID_CHECKIN_START = 23 → heure actuelle toujours < 23 en CI nocturne
            // On force 25 (impossible) pour simuler "toujours trop tôt" dans tout contexte
            when(configService.getInt(AttendanceConfig.KEY_VALID_CHECKIN_START)).thenReturn(25);

            attendanceService.checkIn(1L);

            verify(attendanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("Ne fait rien si l'utilisateur a déjà pointé aujourd'hui")
        void shouldSkipWhenAlreadyCheckedIn() {
            when(configService.getInt(AttendanceConfig.KEY_VALID_CHECKIN_START)).thenReturn(0);
            when(attendanceRepository.existsByUserIdAndDate(eq(1L), any())).thenReturn(true);

            attendanceService.checkIn(1L);

            verify(attendanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si l'utilisateur n'existe pas")
        void shouldThrowWhenUserNotFound() {
            when(configService.getInt(AttendanceConfig.KEY_VALID_CHECKIN_START)).thenReturn(0);
            when(attendanceRepository.existsByUserIdAndDate(eq(1L), any())).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.checkIn(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Envoie une notification si l'utilisateur est en retard")
        void shouldNotifyWhenLate() {
            Long userId = 1L;
            User user   = buildUser(userId);

            // KEY_VALID_CHECKIN_START = 0 → toujours valide
            // KEY_LATE_HOUR = 0, KEY_LATE_MINUTE = 0 → toujours late
            when(configService.getInt(AttendanceConfig.KEY_VALID_CHECKIN_START)).thenReturn(0);
            when(attendanceRepository.existsByUserIdAndDate(eq(userId), any())).thenReturn(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(configService.getInt(AttendanceConfig.KEY_LATE_HOUR)).thenReturn(0);
            when(configService.getInt(AttendanceConfig.KEY_LATE_MINUTE)).thenReturn(0);
            when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            attendanceService.checkIn(userId);

            verify(notificationService).notifyLateArrival(eq(userId), anyString());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // checkOutOnLogout
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("checkOutOnLogout()")
    class CheckOutOnLogout {

        @Test
        @DisplayName("Enregistre le checkout si un check-in existe aujourd'hui")
        void shouldRecordCheckout() {
            Long userId = 1L;
            LocalDate today = LocalDate.now();
            Attendance attendance = buildAttendance(userId, today,
                    today.atTime(8, 0));

            when(attendanceRepository.findByUserIdAndDate(userId, today))
                    .thenReturn(Optional.of(attendance));
            stubDurationConfig(8.0, 4, 17);

            attendanceService.checkOutOnLogout(userId, null);

            assertThat(attendance.getCheckOut()).isNotNull();
            verify(attendanceRepository).save(attendance);
        }

        @Test
        @DisplayName("Ajoute les notes si fournies")
        void shouldSetNotesWhenProvided() {
            Long userId = 1L;
            LocalDate today = LocalDate.now();
            Attendance attendance = buildAttendance(userId, today, today.atTime(8, 0));

            when(attendanceRepository.findByUserIdAndDate(userId, today))
                    .thenReturn(Optional.of(attendance));
            stubDurationConfig(8.0, 4, 17);

            attendanceService.checkOutOnLogout(userId, "Leaving early");

            assertThat(attendance.getNotes()).isEqualTo("Leaving early");
        }

        @Test
        @DisplayName("Ne fait rien si aucun check-in trouvé aujourd'hui")
        void shouldDoNothingWhenNoCheckIn() {
            when(attendanceRepository.findByUserIdAndDate(eq(1L), any()))
                    .thenReturn(Optional.empty());

            attendanceService.checkOutOnLogout(1L, null);

            verify(attendanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("Envoie notification si départ anticipé")
        void shouldNotifyEarlyDeparture() {
            Long userId = 1L;
            LocalDate today = LocalDate.now();
            // Check-in à 8h, checkout simulé par le service → on force earlyDepartureHour = 23
            // pour déclencher EARLY_DEPARTURE : checkOut.getHour() < 23 → toujours vrai
            Attendance attendance = buildAttendance(userId, today, today.atTime(8, 0));

            when(attendanceRepository.findByUserIdAndDate(userId, today))
                    .thenReturn(Optional.of(attendance));
            // halfDayThreshold=1 → worked > 1 → pas HALF_DAY
            // earlyDepartureHour=23 → checkout hour < 23 → EARLY_DEPARTURE
            stubDurationConfig(8.0, 1, 23);

            attendanceService.checkOutOnLogout(userId, null);

            assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.EARLY_DEPARTURE);
            verify(notificationService).notifyEarlyDeparture(eq(userId), anyString());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getMyAttendance
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getMyAttendance()")
    class GetMyAttendance {

        @Test
        @DisplayName("Retourne les enregistrements filtrés par mois et année")
        void shouldReturnByMonthAndYear() {
            Attendance a = buildAttendance(1L, LocalDate.now(), LocalDateTime.now());
            when(attendanceRepository.findByUserIdAndMonthAndYear(1L, 4, 2026))
                    .thenReturn(List.of(a));
            when(attendanceMapper.toResponseDTO(a)).thenReturn(new AttendanceResponseDTO());

            List<AttendanceResponseDTO> result =
                    attendanceService.getMyAttendance(1L, buildFilter(4, 2026));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Retourne les enregistrements filtrés par année uniquement si mois absent")
        void shouldReturnByYearWhenNoMonth() {
            Attendance a = buildAttendance(1L, LocalDate.now(), LocalDateTime.now());
            when(attendanceRepository.findByUserIdAndYear(1L, 2026)).thenReturn(List.of(a));
            when(attendanceMapper.toResponseDTO(a)).thenReturn(new AttendanceResponseDTO());

            List<AttendanceResponseDTO> result =
                    attendanceService.getMyAttendance(1L, buildFilter(null, 2026));

            assertThat(result).hasSize(1);
            verify(attendanceRepository).findByUserIdAndYear(1L, 2026);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getMyDayRecord
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getMyDayRecord()")
    class GetMyDayRecord {

        @Test
        @DisplayName("Retourne l'enregistrement du jour si existant")
        void shouldReturnRecord() {
            LocalDate date = LocalDate.now();
            Attendance a   = buildAttendance(1L, date, date.atTime(8, 0));
            AttendanceResponseDTO dto = new AttendanceResponseDTO();

            when(attendanceRepository.findByUserIdAndDate(1L, date)).thenReturn(Optional.of(a));
            when(attendanceMapper.toResponseDTO(a)).thenReturn(dto);

            AttendanceResponseDTO result = attendanceService.getMyDayRecord(1L, date);

            assertThat(result).isSameAs(dto);
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si aucun enregistrement pour ce jour")
        void shouldThrowWhenNotFound() {
            LocalDate date = LocalDate.now();
            when(attendanceRepository.findByUserIdAndDate(1L, date)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.getMyDayRecord(1L, date))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(date.toString());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getAllAttendance
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getAllAttendance()")
    class GetAllAttendance {

        @Test
        @DisplayName("Retourne tous les enregistrements pour un mois/année")
        void shouldReturnAll() {
            Attendance a = buildAttendance(1L, LocalDate.now(), LocalDateTime.now());
            when(attendanceRepository.findAllByMonthAndYear(4, 2026)).thenReturn(List.of(a));
            when(attendanceMapper.toResponseDTO(a)).thenReturn(new AttendanceResponseDTO());

            List<AttendanceResponseDTO> result =
                    attendanceService.getAllAttendance(buildFilter(4, 2026));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Utilise le mois/année courant si le filtre est null")
        void shouldUseCurrentMonthYearWhenFilterNull() {
            when(attendanceRepository.findAllByMonthAndYear(anyInt(), anyInt()))
                    .thenReturn(List.of());

            attendanceService.getAllAttendance(buildFilter(null, null));

            verify(attendanceRepository).findAllByMonthAndYear(
                    eq(LocalDate.now().getMonthValue()),
                    eq(LocalDate.now().getYear()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getTeamAttendance
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getTeamAttendance()")
    class GetTeamAttendance {

        @Test
        @DisplayName("Retourne les enregistrements de l'équipe du PM")
        void shouldReturnTeamAttendance() {
            User emp = buildUser(10L);
            TeamAssignment assignment = mock(TeamAssignment.class);
            when(assignment.getActive()).thenReturn(true);
            when(assignment.getEmployee()).thenReturn(emp);

            Attendance a = buildAttendance(10L, LocalDate.now(), LocalDateTime.now());

            when(teamAssignmentRepository.findByAssigningManagerId(1L))
                    .thenReturn(List.of(assignment));
            when(attendanceRepository.findByUserIdInAndMonthAndYear(anySet(), eq(4), eq(2026)))
                    .thenReturn(List.of(a));
            when(attendanceMapper.toResponseDTO(a)).thenReturn(new AttendanceResponseDTO());

            List<AttendanceResponseDTO> result =
                    attendanceService.getTeamAttendance(1L, buildFilter(4, 2026));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Retourne liste vide si le PM n'a pas d'équipe")
        void shouldReturnEmptyWhenNoTeam() {
            when(teamAssignmentRepository.findByAssigningManagerId(1L)).thenReturn(List.of());

            List<AttendanceResponseDTO> result =
                    attendanceService.getTeamAttendance(1L, buildFilter(4, 2026));

            assertThat(result).isEmpty();
            verify(attendanceRepository, never()).findByUserIdInAndMonthAndYear(any(), anyInt(), anyInt());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // fixMissedCheckout
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("fixMissedCheckout()")
    class FixMissedCheckout {

        @Test
        @DisplayName("Enregistre un checkout rétroactif avec succès")
        void shouldRecordRetroactiveCheckout() {
            Long userId    = 1L;
            LocalDate yesterday = LocalDate.now().minusDays(1);
            Attendance attendance = buildAttendance(userId, yesterday, yesterday.atTime(8, 0));

            when(attendanceRepository.findByUserIdAndDate(userId, yesterday))
                    .thenReturn(Optional.of(attendance));
            stubDurationConfig(8.0, 4, 17);
            when(attendanceRepository.save(any())).thenReturn(attendance);
            when(attendanceMapper.toResponseDTO(any())).thenReturn(new AttendanceResponseDTO());

            AttendanceResponseDTO result = attendanceService.fixMissedCheckout(userId, "17:00");

            assertThat(attendance.getCheckOut()).isNotNull();
            assertThat(attendance.getNotes()).contains("retroactively");
            verify(attendanceRepository).save(attendance);
        }

        @Test
        @DisplayName("Lève BusinessException si aucun check-in hier")
        void shouldThrowWhenNoCheckInYesterday() {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            when(attendanceRepository.findByUserIdAndDate(1L, yesterday))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.fixMissedCheckout(1L, "17:00"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No check-in found for yesterday");
        }

        @Test
        @DisplayName("Lève BusinessException si le checkout est déjà enregistré")
        void shouldThrowWhenCheckoutAlreadyRecorded() {
            Long userId    = 1L;
            LocalDate yesterday = LocalDate.now().minusDays(1);
            Attendance attendance = buildAttendance(userId, yesterday, yesterday.atTime(8, 0));
            attendance.setCheckOut(yesterday.atTime(17, 0));

            when(attendanceRepository.findByUserIdAndDate(userId, yesterday))
                    .thenReturn(Optional.of(attendance));

            assertThatThrownBy(() -> attendanceService.fixMissedCheckout(userId, "18:00"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already recorded");
        }

        @Test
        @DisplayName("Lève BusinessException si le checkout est avant le check-in")
        void shouldThrowWhenCheckoutBeforeCheckIn() {
            Long userId    = 1L;
            LocalDate yesterday = LocalDate.now().minusDays(1);
            Attendance attendance = buildAttendance(userId, yesterday, yesterday.atTime(10, 0));

            when(attendanceRepository.findByUserIdAndDate(userId, yesterday))
                    .thenReturn(Optional.of(attendance));

            assertThatThrownBy(() -> attendanceService.fixMissedCheckout(userId, "09:00"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("after check-in");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // hasMissedCheckout
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("hasMissedCheckout()")
    class HasMissedCheckout {

        @Test
        @DisplayName("Retourne true si le checkout d'hier est manquant")
        void shouldReturnTrueWhenCheckoutMissing() {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            Attendance a = buildAttendance(1L, yesterday, yesterday.atTime(8, 0));
            // checkout null (déjà null par défaut dans le builder)

            when(attendanceRepository.findByUserIdAndDate(1L, yesterday))
                    .thenReturn(Optional.of(a));

            assertThat(attendanceService.hasMissedCheckout(1L)).isTrue();
        }

        @Test
        @DisplayName("Retourne false si le checkout d'hier est enregistré")
        void shouldReturnFalseWhenCheckoutPresent() {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            Attendance a = buildAttendance(1L, yesterday, yesterday.atTime(8, 0));
            a.setCheckOut(yesterday.atTime(17, 0));

            when(attendanceRepository.findByUserIdAndDate(1L, yesterday))
                    .thenReturn(Optional.of(a));

            assertThat(attendanceService.hasMissedCheckout(1L)).isFalse();
        }

        @Test
        @DisplayName("Retourne false si aucun enregistrement hier")
        void shouldReturnFalseWhenNoRecord() {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            when(attendanceRepository.findByUserIdAndDate(1L, yesterday))
                    .thenReturn(Optional.empty());

            assertThat(attendanceService.hasMissedCheckout(1L)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // detectMissedCheckouts
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("detectMissedCheckouts()")
    class DetectMissedCheckouts {

        @Test
        @DisplayName("Envoie une notification pour chaque check-in sans checkout")
        void shouldNotifyForEachMissedCheckout() {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            User user = buildUser(10L);
            Attendance a = buildAttendance(10L, yesterday, yesterday.atTime(8, 0));

            when(attendanceRepository.findByDateAndCheckOutIsNull(yesterday))
                    .thenReturn(List.of(a));

            attendanceService.detectMissedCheckouts();

            verify(notificationService).notifyMissedCheckout(eq(10L), anyString());
        }

        @Test
        @DisplayName("N'envoie pas de notification si la liste est vide")
        void shouldNotNotifyWhenNoMissedCheckouts() {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            when(attendanceRepository.findByDateAndCheckOutIsNull(yesterday))
                    .thenReturn(List.of());

            attendanceService.detectMissedCheckouts();

            verify(notificationService, never()).notifyMissedCheckout(any(), any());
        }

        @Test
        @DisplayName("N'envoie pas de notification si checkIn est null")
        void shouldSkipWhenCheckInIsNull() {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            User user = buildUser(10L);
            // Attendance sans checkIn
            Attendance a = Attendance.builder()
                    .user(user).date(yesterday).checkIn(null)
                    .status(AttendanceStatus.ABSENT).overtimeHours(0.0).build();

            when(attendanceRepository.findByDateAndCheckOutIsNull(yesterday))
                    .thenReturn(List.of(a));

            attendanceService.detectMissedCheckouts();

            verify(notificationService, never()).notifyMissedCheckout(any(), any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // computeDuration (via checkOutOnLogout) — statuts
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Calcul du statut lors du checkout")
    class ComputeDuration {

        @Test
        @DisplayName("Statut HALF_DAY si durée < halfDayThreshold")
        void shouldSetHalfDayWhenWorkedLessThanThreshold() {
            Long userId = 1L;
            LocalDate today = LocalDate.now();

            // ✅ checkIn il y a 30 minutes → toujours < halfDayThreshold=4h,
            //    quelle que soit l'heure d'exécution du test
            Attendance attendance = buildAttendance(userId, today,
                    LocalDateTime.now().minusMinutes(30));

            when(attendanceRepository.findByUserIdAndDate(userId, today))
                    .thenReturn(Optional.of(attendance));
            // earlyDepartureHour=0 → 0 < 0 = false → jamais EARLY_DEPARTURE
            // halfDayThreshold=4  → 0.5h < 4h → HALF_DAY
            stubDurationConfig(8.0, 4, 0);

            attendanceService.checkOutOnLogout(userId, null);

            assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.HALF_DAY);
        }
        @Test
        @DisplayName("Calcule correctement les heures supplémentaires")
        void shouldComputeOvertimeCorrectly() {
            Long userId = 1L;
            LocalDate today = LocalDate.now();
            // checkIn à 08:00 → checkout à maintenant → durée > 8h si test en journée
            // On force via config: standardHours=0.0 → tout est overtime
            Attendance attendance = buildAttendance(userId, today, today.atTime(8, 0));

            when(attendanceRepository.findByUserIdAndDate(userId, today))
                    .thenReturn(Optional.of(attendance));
            // standardHours=0 → tout est overtime, halfDayThreshold=0, earlyDeparture=0
            stubDurationConfig(0.0, 0, 0);

            attendanceService.checkOutOnLogout(userId, null);

            assertThat(attendance.getOvertimeHours()).isGreaterThan(0.0);
        }
    }
}