package com.example.pfe.Service;

import com.example.pfe.Repository.MoodRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.MoodDTO;
import com.example.pfe.entities.MoodEntry;
import com.example.pfe.entities.User;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MoodService {

    private final MoodRepository moodRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM d");

    // ── Employee: submit mood ─────────────────────────────

    @Transactional
    public void submit(Long userId, int score, String note) {
        if (score < 1 || score > 5)
            throw new BusinessException("Score must be between 1 and 5");

        LocalDate today = LocalDate.now();
        if (moodRepository.existsByUserIdAndDate(userId, today))
            throw new BusinessException("You already submitted your mood today");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String dept = user.getDepartment() != null
                ? user.getDepartment().name() : "UNKNOWN";

        moodRepository.save(MoodEntry.builder()
                .userId(userId)
                .department(dept)
                .date(today)
                .score(score)
                .note(note != null && !note.isBlank() ? note.trim() : null)
                .build());
    }

    // ── Employee: check today's status ────────────────────

    @Transactional(readOnly = true)
    public MoodDTO.StatusDTO getStatus(Long userId) {
        LocalDate today   = LocalDate.now();
        long total        = userRepository.countByActiveTrue();
        long submitted    = moodRepository.countByDate(today);
        double rate       = total > 0 ? (submitted * 100.0 / total) : 0;

        Optional<MoodEntry> mine = moodRepository.findByUserIdAndDate(userId, today);

        return MoodDTO.StatusDTO.builder()
                .submitted(mine.isPresent())
                .score(mine.map(MoodEntry::getScore).orElse(null))
                .teamTotal(submitted)
                .totalEmployees(total)
                .participationRate(Math.round(rate * 10.0) / 10.0)
                .build();
    }

    // ── Admin: full overview ──────────────────────────────

    @Transactional(readOnly = true)
    public MoodDTO.OverviewDTO getOverview(LocalDate date, String department) {
        if (date == null) date = LocalDate.now();

        long totalEmployees = userRepository.countByActiveTrue();
        List<MoodEntry> entries = moodRepository.findAllByDate(date);

        // Filter by dept if requested
        boolean filterDept = department != null
                && !department.isBlank()
                && !"ALL".equalsIgnoreCase(department);
        List<MoodEntry> filtered = filterDept
                ? entries.stream().filter(e -> department.equalsIgnoreCase(e.getDepartment())).toList()
                : entries;

        long   total   = filtered.size();
        double avg     = filtered.stream().mapToInt(MoodEntry::getScore).average().orElse(0.0);
        double rate    = totalEmployees > 0 ? (total * 100.0 / totalEmployees) : 0;

        // Score distribution
        Map<Integer, Long> dist = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            final int s = i;
            dist.put(i, filtered.stream().filter(e -> e.getScore() == s).count());
        }

        // By department
        List<MoodDTO.DeptMood> byDept = entries.stream()
                .collect(Collectors.groupingBy(MoodEntry::getDepartment))
                .entrySet().stream()
                .map(e -> MoodDTO.DeptMood.builder()
                        .department(e.getKey())
                        .averageScore(round(e.getValue().stream()
                                .mapToInt(MoodEntry::getScore).average().orElse(0)))
                        .responses(e.getValue().size())
                        .build())
                .sorted(Comparator.comparingDouble(MoodDTO.DeptMood::getAverageScore).reversed())
                .toList();

        // 14-day trend
        LocalDate finalDate = date;
        List<MoodDTO.TrendPoint> trend = finalDate.minusDays(13)
                .datesUntil(finalDate.plusDays(1))
                .map(d -> {
                    List<MoodEntry> day = moodRepository.findAllByDate(d);
                    double davg = day.stream().mapToInt(MoodEntry::getScore).average().orElse(0);
                    return MoodDTO.TrendPoint.builder()
                            .date(d.format(FMT))
                            .averageScore(round(davg))
                            .responses(day.size())
                            .build();
                })
                .toList();

        // Anonymous notes — shuffle for privacy, take last 5
        List<String> notes = filtered.stream()
                .map(MoodEntry::getNote)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> { Collections.shuffle(list); return list; }
                ))
                .stream().limit(5).toList();

        return MoodDTO.OverviewDTO.builder()
                .totalEmployees(totalEmployees)
                .totalResponses(total)
                .participationRate(round(rate))
                .averageScore(round(avg))
                .distribution(dist)
                .byDepartment(byDept)
                .trend(trend)
                .recentNotes(notes)
                .build();
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}