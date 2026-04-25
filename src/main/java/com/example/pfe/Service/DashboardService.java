package com.example.pfe.Service;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.DashboardStatsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;

    public DashboardStatsDTO getStats() {
        var allUsers = userRepository.findAll();
        var now      = LocalDate.now();

        // ── Total actifs ──────────────────────────────────
        long total = allUsers.stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .count();

        // ── Nouvelles embauches ce mois ───────────────────
        int newHires = (int) allUsers.stream()
                .filter(u -> u.getHireDate() != null
                        && u.getHireDate().getYear()       == now.getYear()
                        && u.getHireDate().getMonthValue() == now.getMonthValue())
                .count();

        // ── Répartition par département ───────────────────
        var byDept = allUsers.stream()
                .filter(u -> u.getDepartment() != null
                        && Boolean.TRUE.equals(u.getActive()))
                .collect(Collectors.groupingBy(
                        u -> u.getDepartment().name(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .map(e -> DashboardStatsDTO.DeptStatDTO.builder()
                        .department(e.getKey())
                        .count(e.getValue())
                        .build())
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());

        return DashboardStatsDTO.builder()
                .totalEmployees(total)
                .newHiresThisMonth(newHires)
                .byDepartment(byDept)
                .build();
    }
}