package com.example.pfe.Service;

import com.example.pfe.Repository.LeaveBalanceRepository;
import com.example.pfe.Repository.LeaveRequestRepository;
import com.example.pfe.Repository.TeamAssignmentRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.*;
import com.example.pfe.entities.LeaveBalance;
import com.example.pfe.entities.LeaveRequest;
import com.example.pfe.entities.TeamAssignment;
import com.example.pfe.entities.User;
import com.example.pfe.enums.LeaveStatus;
import com.example.pfe.enums.LeaveType;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.LeaveMapper;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveService - Tests Unitaires")
class LeaveServiceTest {

    @Mock private LeaveRequestRepository   leaveRequestRepository;
    @Mock private LeaveBalanceRepository   leaveBalanceRepository;
    @Mock private UserRepository           userRepository;
    @Mock private TeamAssignmentRepository teamAssignmentRepository;
    @Mock private LeaveMapper              leaveMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private LeaveService leaveService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private User buildUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setFirstName("John");
        u.setLastName("Doe");
        u.setEmail("john@test.com");
        return u;
    }

    private LeaveBalance buildBalance(Long userId, double annualTotal, double annualTaken,
                                      double sickTotal, double sickTaken) {
        User user = buildUser(userId);
        return LeaveBalance.builder()
                .user(user)
                .year(LocalDate.now().getYear())
                .annualTotal(annualTotal).annualTaken(annualTaken)
                .sickTotal(sickTotal).sickTaken(sickTaken)
                .unpaidTotal(0.0).unpaidTaken(0.0)
                .build();
    }

    private LeaveRequest buildRequest(Long id, Long userId, LeaveStatus status,
                                      LocalDate start, LocalDate end) {
        User user = buildUser(userId);
        return LeaveRequest.builder()
                .user(user)
                .leaveType(LeaveType.ANNUAL)
                .startDate(start)
                .endDate(end)
                .daysCount(1.0)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private LeaveRequestDTO buildRequestDTO(LocalDate start, LocalDate end, LeaveType type) {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setLeaveType(type);
        dto.setStartDate(start);
        dto.setEndDate(end);
        dto.setReason("Test reason");
        return dto;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // requestLeave
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("requestLeave()")
    class RequestLeave {

        @Test
        @DisplayName("Crée une demande de congé ANNUAL avec succès")
        void shouldCreateAnnualLeaveSuccessfully() {
            Long userId = 1L;
            LocalDate start = LocalDate.now().plusDays(1);
            LocalDate end   = LocalDate.now().plusDays(3);
            User user = buildUser(userId);
            LeaveBalance balance = buildBalance(userId, 22.0, 0.0, 15.0, 0.0);
            LeaveRequest saved = buildRequest(1L, userId, LeaveStatus.PENDING, start, end);

            when(leaveRequestRepository.existsOverlappingLeave(userId, start, end)).thenReturn(false);
            when(leaveBalanceRepository.findByUserIdAndYear(eq(userId), anyInt()))
                    .thenReturn(Optional.of(balance));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(leaveRequestRepository.save(any())).thenReturn(saved);
            when(leaveMapper.toResponseDTO(saved)).thenReturn(new LeaveResponseDTO());

            LeaveResponseDTO result = leaveService.requestLeave(userId, buildRequestDTO(start, end, LeaveType.ANNUAL));

            assertThat(result).isNotNull();
            verify(leaveRequestRepository).save(any(LeaveRequest.class));
        }

        @Test
        @DisplayName("Crée une demande UNPAID sans vérifier le solde")
        void shouldCreateUnpaidLeaveWithoutBalanceCheck() {
            Long userId = 1L;
            LocalDate start = LocalDate.now().plusDays(1);
            LocalDate end   = LocalDate.now().plusDays(2);
            User user = buildUser(userId);
            LeaveRequest saved = buildRequest(1L, userId, LeaveStatus.PENDING, start, end);

            when(leaveRequestRepository.existsOverlappingLeave(userId, start, end)).thenReturn(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(leaveRequestRepository.save(any())).thenReturn(saved);
            when(leaveMapper.toResponseDTO(saved)).thenReturn(new LeaveResponseDTO());

            leaveService.requestLeave(userId, buildRequestDTO(start, end, LeaveType.UNPAID));

            verify(leaveBalanceRepository, never()).findByUserIdAndYear(any(), anyInt());
        }

        @Test
        @DisplayName("Lève BusinessException si la date de début est dans le passé")
        void shouldThrowWhenStartDateInPast() {
            LocalDate start = LocalDate.now().minusDays(1);
            LocalDate end   = LocalDate.now().plusDays(1);

            assertThatThrownBy(() ->
                    leaveService.requestLeave(1L, buildRequestDTO(start, end, LeaveType.ANNUAL)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("past");
        }

        @Test
        @DisplayName("Lève BusinessException si la date de fin est avant la date de début")
        void shouldThrowWhenEndBeforeStart() {
            LocalDate start = LocalDate.now().plusDays(3);
            LocalDate end   = LocalDate.now().plusDays(1);

            assertThatThrownBy(() ->
                    leaveService.requestLeave(1L, buildRequestDTO(start, end, LeaveType.ANNUAL)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("End date must be after start date");
        }

        @Test
        @DisplayName("Lève BusinessException si la période ne contient aucun jour ouvrable")
        void shouldThrowWhenNoWorkingDays() {
            // Trouver le prochain samedi
            LocalDate day = LocalDate.now().plusDays(1);
            while (day.getDayOfWeek().getValue() != 6) day = day.plusDays(1);
            // Extraire dans des variables effectively final pour le lambda
            final LocalDate nextSaturday = day;
            final LocalDate nextSunday   = day.plusDays(1);

            assertThatThrownBy(() ->
                    leaveService.requestLeave(1L, buildRequestDTO(nextSaturday, nextSunday, LeaveType.ANNUAL)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("no working days");
        }

        @Test
        @DisplayName("Lève BusinessException si les dates se chevauchent avec une demande existante")
        void shouldThrowWhenOverlapping() {
            LocalDate start = LocalDate.now().plusDays(1);
            LocalDate end   = LocalDate.now().plusDays(3);

            when(leaveRequestRepository.existsOverlappingLeave(1L, start, end)).thenReturn(true);

            assertThatThrownBy(() ->
                    leaveService.requestLeave(1L, buildRequestDTO(start, end, LeaveType.ANNUAL)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("overlapping");
        }

        @Test
        @DisplayName("Lève BusinessException si le solde est insuffisant")
        void shouldThrowWhenInsufficientBalance() {
            Long userId = 1L;
            LocalDate start = LocalDate.now().plusDays(1);
            LocalDate end   = LocalDate.now().plusDays(5); // ~3 working days
            LeaveBalance balance = buildBalance(userId, 1.0, 1.0, 15.0, 0.0); // 0 annual left

            when(leaveRequestRepository.existsOverlappingLeave(userId, start, end)).thenReturn(false);
            when(leaveBalanceRepository.findByUserIdAndYear(eq(userId), anyInt()))
                    .thenReturn(Optional.of(balance));

            assertThatThrownBy(() ->
                    leaveService.requestLeave(userId, buildRequestDTO(start, end, LeaveType.ANNUAL)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Insufficient");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getMyLeaves
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getMyLeaves()")
    class GetMyLeaves {

        @Test
        @DisplayName("Retourne les demandes de l'utilisateur triées par date")
        void shouldReturnUserLeaves() {
            LeaveRequest req = buildRequest(1L, 1L, LeaveStatus.PENDING,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
            when(leaveRequestRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(req));
            when(leaveMapper.toResponseDTO(req)).thenReturn(new LeaveResponseDTO());

            List<LeaveResponseDTO> result = leaveService.getMyLeaves(1L);

            assertThat(result).hasSize(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // approveLeave
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("approveLeave()")
    class ApproveLeave {

        @Test
        @DisplayName("Approuve une demande en attente et déduit le solde")
        void shouldApproveAndDeductBalance() {
            Long requestId = 1L;
            Long adminId   = 99L;
            User admin     = buildUser(adminId);
            User employee  = buildUser(10L);
            LeaveRequest request = buildRequest(requestId, 10L, LeaveStatus.PENDING,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
            LeaveBalance balance = buildBalance(10L, 22.0, 0.0, 15.0, 0.0);

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(leaveBalanceRepository.findByUserIdAndYear(eq(10L), anyInt()))
                    .thenReturn(Optional.of(balance));
            when(leaveRequestRepository.save(any())).thenReturn(request);
            when(leaveMapper.toResponseDTO(any())).thenReturn(new LeaveResponseDTO());

            leaveService.approveLeave(requestId, adminId);

            assertThat(request.getStatus()).isEqualTo(LeaveStatus.APPROVED);
            assertThat(request.getApprovedBy()).isSameAs(admin);
            verify(leaveBalanceRepository).save(any());
            verify(notificationService).notifyLeaveApproved(eq(10L), any(), any(), any());
        }

        @Test
        @DisplayName("Lève BusinessException si la demande n'est pas en attente")
        void shouldThrowWhenNotPending() {
            LeaveRequest request = buildRequest(1L, 10L, LeaveStatus.APPROVED,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));

            when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> leaveService.approveLeave(1L, 99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("approved");
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si la demande n'existe pas")
        void shouldThrowWhenRequestNotFound() {
            when(leaveRequestRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveService.approveLeave(99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // rejectLeave
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("rejectLeave()")
    class RejectLeave {

        private LeaveDecisionDTO buildDecision(String reason) {
            LeaveDecisionDTO dto = new LeaveDecisionDTO();
            dto.setRejectionReason(reason);
            return dto;
        }

        @Test
        @DisplayName("Rejette une demande en attente")
        void shouldRejectSuccessfully() {
            Long requestId = 1L;
            Long adminId   = 99L;
            User admin     = buildUser(adminId);
            LeaveRequest request = buildRequest(requestId, 10L, LeaveStatus.PENDING,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(leaveRequestRepository.save(any())).thenReturn(request);
            when(leaveMapper.toResponseDTO(any())).thenReturn(new LeaveResponseDTO());

            leaveService.rejectLeave(requestId, adminId, buildDecision("Not enough coverage"));

            assertThat(request.getStatus()).isEqualTo(LeaveStatus.REJECTED);
            assertThat(request.getRejectionReason()).isEqualTo("Not enough coverage");
            verify(notificationService).notifyLeaveRejected(eq(10L), any(), eq("Not enough coverage"));
        }

        @Test
        @DisplayName("Lève BusinessException si la demande n'est pas en attente")
        void shouldThrowWhenNotPending() {
            LeaveRequest request = buildRequest(1L, 10L, LeaveStatus.REJECTED,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));

            when(leaveRequestRepository.findById(1L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> leaveService.rejectLeave(1L, 99L, buildDecision("reason")))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getPendingLeaves / getAllLeaves
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getPendingLeaves() & getAllLeaves()")
    class GetLeaves {

        @Test
        @DisplayName("getPendingLeaves retourne uniquement les demandes en attente")
        void shouldReturnPendingLeaves() {
            LeaveRequest req = buildRequest(1L, 10L, LeaveStatus.PENDING,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
            when(leaveRequestRepository.findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING))
                    .thenReturn(List.of(req));
            when(leaveMapper.toResponseDTO(req)).thenReturn(new LeaveResponseDTO());

            List<LeaveResponseDTO> result = leaveService.getPendingLeaves();

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getAllLeaves retourne toutes les demandes")
        void shouldReturnAllLeaves() {
            LeaveRequest req = buildRequest(1L, 10L, LeaveStatus.APPROVED,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
            when(leaveRequestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(req));
            when(leaveMapper.toResponseDTO(req)).thenReturn(new LeaveResponseDTO());

            List<LeaveResponseDTO> result = leaveService.getAllLeaves();

            assertThat(result).hasSize(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getTeamAllLeaves / getTeamPendingLeaves
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Opérations PM — équipe")
    class TeamLeaves {

        private TeamAssignment buildAssignment(Long employeeId, boolean active) {
            User emp = buildUser(employeeId);
            TeamAssignment a = mock(TeamAssignment.class);
            when(a.getActive()).thenReturn(active);
            when(a.getEmployee()).thenReturn(emp);
            return a;
        }

        @Test
        @DisplayName("getTeamAllLeaves retourne les demandes de l'équipe")
        void shouldReturnTeamAllLeaves() {
            Long pmId = 5L;
            TeamAssignment a = buildAssignment(10L, true);
            LeaveRequest req = buildRequest(1L, 10L, LeaveStatus.PENDING,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));

            when(teamAssignmentRepository.findByAssigningManagerId(pmId)).thenReturn(List.of(a));
            when(leaveRequestRepository.findByUserIdInOrderByCreatedAtDesc(Set.of(10L)))
                    .thenReturn(List.of(req));
            when(leaveMapper.toResponseDTO(req)).thenReturn(new LeaveResponseDTO());

            List<LeaveResponseDTO> result = leaveService.getTeamAllLeaves(pmId);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getTeamAllLeaves retourne liste vide si pas d'équipe")
        void shouldReturnEmptyWhenNoTeam() {
            when(teamAssignmentRepository.findByAssigningManagerId(5L)).thenReturn(List.of());

            List<LeaveResponseDTO> result = leaveService.getTeamAllLeaves(5L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getTeamPendingLeaves retourne uniquement les demandes en attente de l'équipe")
        void shouldReturnTeamPendingLeaves() {
            Long pmId = 5L;
            TeamAssignment a = buildAssignment(10L, true);
            LeaveRequest req = buildRequest(1L, 10L, LeaveStatus.PENDING,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));

            when(teamAssignmentRepository.findByAssigningManagerId(pmId)).thenReturn(List.of(a));
            when(leaveRequestRepository.findByUserIdInAndStatusOrderByCreatedAtAsc(Set.of(10L), LeaveStatus.PENDING))
                    .thenReturn(List.of(req));
            when(leaveMapper.toResponseDTO(req)).thenReturn(new LeaveResponseDTO());

            List<LeaveResponseDTO> result = leaveService.getTeamPendingLeaves(pmId);

            assertThat(result).hasSize(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // approveLeaveByPM / rejectLeaveByPM
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("approveLeaveByPM() & rejectLeaveByPM()")
    class PMActions {

        private TeamAssignment buildActiveAssignment(Long empId) {
            User emp = buildUser(empId);
            TeamAssignment a = mock(TeamAssignment.class);
            when(a.getActive()).thenReturn(true);
            when(a.getEmployee()).thenReturn(emp);
            return a;
        }

        @Test
        @DisplayName("PM approuve si l'employé fait partie de son équipe")
        void shouldApprovePMLeave() {
            Long pmId      = 5L;
            Long requestId = 1L;
            User pm        = buildUser(pmId);
            User employee  = buildUser(10L);

            LeaveRequest request = buildRequest(requestId, 10L, LeaveStatus.PENDING,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
            LeaveBalance balance = buildBalance(10L, 22.0, 0.0, 15.0, 0.0);
            TeamAssignment a = buildActiveAssignment(10L);

            // assertIsTeamMember check
            when(teamAssignmentRepository.findByAssigningManagerId(pmId)).thenReturn(List.of(a));
            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(userRepository.findById(pmId)).thenReturn(Optional.of(pm));
            when(leaveBalanceRepository.findByUserIdAndYear(eq(10L), anyInt()))
                    .thenReturn(Optional.of(balance));
            when(leaveRequestRepository.save(any())).thenReturn(request);
            when(leaveMapper.toResponseDTO(any())).thenReturn(new LeaveResponseDTO());

            leaveService.approveLeaveByPM(requestId, pmId);

            assertThat(request.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        }

        @Test
        @DisplayName("Lève BusinessException si l'employé n'est pas dans l'équipe du PM")
        void shouldThrowWhenNotTeamMember() {
            Long pmId      = 5L;
            Long requestId = 1L;

            // Équipe du PM ne contient pas l'employé 10L
            User otherEmp = buildUser(99L);
            TeamAssignment otherA = mock(TeamAssignment.class);
            when(otherA.getActive()).thenReturn(true);
            when(otherA.getEmployee()).thenReturn(otherEmp);

            LeaveRequest request = buildRequest(requestId, 10L, LeaveStatus.PENDING,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(teamAssignmentRepository.findByAssigningManagerId(pmId)).thenReturn(List.of(otherA));

            assertThatThrownBy(() -> leaveService.approveLeaveByPM(requestId, pmId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Access denied");
        }

        @Test
        @DisplayName("PM rejette si l'employé fait partie de son équipe")
        void shouldRejectPMLeave() {
            Long pmId      = 5L;
            Long requestId = 1L;
            User pm        = buildUser(pmId);

            LeaveRequest request = buildRequest(requestId, 10L, LeaveStatus.PENDING,
                    LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
            TeamAssignment a = buildActiveAssignment(10L);
            LeaveDecisionDTO decision = new LeaveDecisionDTO();
            decision.setRejectionReason("Not approved");

            when(leaveRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(teamAssignmentRepository.findByAssigningManagerId(pmId)).thenReturn(List.of(a));
            when(userRepository.findById(pmId)).thenReturn(Optional.of(pm));
            when(leaveRequestRepository.save(any())).thenReturn(request);
            when(leaveMapper.toResponseDTO(any())).thenReturn(new LeaveResponseDTO());

            leaveService.rejectLeaveByPM(requestId, pmId, decision);

            assertThat(request.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getMyBalance
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getMyBalance()")
    class GetMyBalance {

        @Test
        @DisplayName("Retourne le solde existant de l'utilisateur")
        void shouldReturnExistingBalance() {
            LeaveBalance balance = buildBalance(1L, 22.0, 5.0, 15.0, 2.0);
            when(leaveBalanceRepository.findByUserIdAndYear(eq(1L), anyInt()))
                    .thenReturn(Optional.of(balance));
            when(leaveMapper.toBalanceDTO(balance)).thenReturn(new LeaveBalanceDTO());

            LeaveBalanceDTO result = leaveService.getMyBalance(1L);

            assertThat(result).isNotNull();
            verify(leaveBalanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("Crée un solde par défaut si aucun n'existe")
        void shouldCreateDefaultBalanceWhenMissing() {
            User user = buildUser(1L);
            LeaveBalance defaultBalance = buildBalance(1L, 22.0, 0.0, 15.0, 0.0);

            when(leaveBalanceRepository.findByUserIdAndYear(eq(1L), anyInt()))
                    .thenReturn(Optional.empty());
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(leaveBalanceRepository.save(any())).thenReturn(defaultBalance);
            when(leaveMapper.toBalanceDTO(defaultBalance)).thenReturn(new LeaveBalanceDTO());

            LeaveBalanceDTO result = leaveService.getMyBalance(1L);

            assertThat(result).isNotNull();
            verify(leaveBalanceRepository).save(any(LeaveBalance.class));
        }
    }
}