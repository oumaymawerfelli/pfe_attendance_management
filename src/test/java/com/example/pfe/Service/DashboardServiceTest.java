package com.example.pfe.Service;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.DashboardStatsDTO;
import com.example.pfe.entities.User;
import com.example.pfe.enums.Department;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService - Tests Unitaires")
class DashboardServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private DashboardService dashboardService;

    // ── Fixture ───────────────────────────────────────────────────────────────

    private User buildUser(boolean active, Department department, LocalDate hireDate) {
        User u = new User();
        u.setActive(active);
        u.setDepartment(department);
        u.setHireDate(hireDate);
        return u;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 1 — Aucun utilisateur
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Retourne des stats vides quand il n'y a aucun utilisateur")
    void shouldReturnZeroStatsWhenNoUsers() {
        when(userRepository.findAll()).thenReturn(List.of());

        DashboardStatsDTO stats = dashboardService.getStats();

        assertThat(stats.getTotalEmployees()).isZero();
        assertThat(stats.getNewHiresThisMonth()).isZero();
        assertThat(stats.getByDepartment()).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 2 — Compter seulement les actifs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Compte uniquement les employés actifs dans totalEmployees")
    void shouldCountOnlyActiveEmployees() {
        User active   = buildUser(true,  null, null);
        User inactive = buildUser(false, null, null);

        when(userRepository.findAll()).thenReturn(List.of(active, inactive));

        DashboardStatsDTO stats = dashboardService.getStats();

        assertThat(stats.getTotalEmployees()).isEqualTo(1);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 3 — Nouvelles embauches du mois courant
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Compte uniquement les embauches du mois et de l'année courants")
    void shouldCountNewHiresThisMonth() {
        // On utilise le mois/année courant pour que le test reste valide dans le temps
        LocalDate now = LocalDate.now();

        User hiredThisMonth = buildUser(false, null, now.withDayOfMonth(1));
        User hiredLastMonth = buildUser(false, null, now.minusMonths(1).withDayOfMonth(1));
        User noHireDate     = buildUser(false, null, null);

        when(userRepository.findAll()).thenReturn(List.of(hiredThisMonth, hiredLastMonth, noHireDate));

        DashboardStatsDTO stats = dashboardService.getStats();

        assertThat(stats.getNewHiresThisMonth()).isEqualTo(1);
    }

    @Test
    @DisplayName("newHiresThisMonth = 0 si toutes les embauches sont dans d'autres mois")
    void shouldReturnZeroNewHiresWhenNoneThisMonth() {
        LocalDate now = LocalDate.now();

        User hiredLastMonth = buildUser(false, null, now.minusMonths(1));
        User hiredLastYear  = buildUser(false, null, now.minusYears(1));

        when(userRepository.findAll()).thenReturn(List.of(hiredLastMonth, hiredLastYear));

        DashboardStatsDTO stats = dashboardService.getStats();

        assertThat(stats.getNewHiresThisMonth()).isZero();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test 4 — Répartition par département triée décroissante
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Groupe les actifs par département et trie par count décroissant")
    void shouldGroupByDepartmentSortedDesc() {
        User dev1     = buildUser(true,  Department.IT, null);
        User dev2     = buildUser(true,  Department.IT, null);
        User hr       = buildUser(true,  Department.HR, null);
        User inactive = buildUser(false, Department.HR, null); // ne doit pas être compté

        when(userRepository.findAll()).thenReturn(List.of(dev1, dev2, hr, inactive));

        DashboardStatsDTO stats = dashboardService.getStats();

        assertThat(stats.getByDepartment()).hasSize(2);

        DashboardStatsDTO.DeptStatDTO first  = stats.getByDepartment().get(0);
        DashboardStatsDTO.DeptStatDTO second = stats.getByDepartment().get(1);

        assertThat(first.getDepartment()).isEqualTo("IT");
        assertThat(first.getCount()).isEqualTo(2);
        assertThat(second.getDepartment()).isEqualTo("HR");
        assertThat(second.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Les utilisateurs sans département ne figurent pas dans byDepartment")
    void shouldExcludeUsersWithNullDepartment() {
        User withDept    = buildUser(true, Department.IT, null);
        User withoutDept = buildUser(true, null,          null);

        when(userRepository.findAll()).thenReturn(List.of(withDept, withoutDept));

        DashboardStatsDTO stats = dashboardService.getStats();

        assertThat(stats.getByDepartment()).hasSize(1);
        assertThat(stats.getByDepartment().get(0).getDepartment()).isEqualTo("IT");
    }

    @Test
    @DisplayName("totalEmployees et byDepartment sont cohérents")
    void shouldHaveConsistentTotals() {
        User dev1 = buildUser(true,  Department.IT, null);
        User dev2 = buildUser(true,  Department.IT, null);
        User hr   = buildUser(true,  Department.HR, null);
        User dis  = buildUser(false, Department.IT, null);

        when(userRepository.findAll()).thenReturn(List.of(dev1, dev2, hr, dis));

        DashboardStatsDTO stats = dashboardService.getStats();

        long sumByDept = stats.getByDepartment().stream()
                .mapToLong(DashboardStatsDTO.DeptStatDTO::getCount)
                .sum();

        assertThat(stats.getTotalEmployees()).isEqualTo(3);
        assertThat(sumByDept).isEqualTo(stats.getTotalEmployees());
    }
}