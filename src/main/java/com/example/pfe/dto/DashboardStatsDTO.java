package com.example.pfe.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DashboardStatsDTO {
    private long   totalEmployees;
    private int    newHiresThisMonth;
    private List<DeptStatDTO> byDepartment;

    @Data
    @Builder
    public static class DeptStatDTO {
        private String department;
        private long   count;
    }
}