package com.example.pfe.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

// ── Submit request ────────────────────────────────────────
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoodDTO {
    private int    score;   // 1–5
    private String note;    // optional, anonymous

    // ── Employee status response ──────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StatusDTO {
        private boolean submitted;
        private Integer score;          // null if not submitted
        private long    teamTotal;      // how many teammates submitted today
        private long    totalEmployees;
        private double  participationRate;
    }

    // ── Admin overview response ───────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OverviewDTO {
        private long               totalEmployees;
        private long               totalResponses;
        private double             participationRate;  // %
        private double             averageScore;       // 1.0–5.0
        private Map<Integer,Long>  distribution;       // {1:x, 2:x, 3:x, 4:x, 5:x}
        private List<DeptMood>     byDepartment;
        private List<TrendPoint>   trend;              // last 14 days
        private List<String>       recentNotes;        // anonymous notes, last 5
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DeptMood {
        private String department;
        private double averageScore;
        private long   responses;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TrendPoint {
        private String date;
        private double averageScore;
        private long   responses;
    }
}