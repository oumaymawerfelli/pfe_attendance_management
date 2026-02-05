package com.example.pfe.Service;

import com.example.pfe.entities.User;
import com.example.pfe.enums.Department;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.UUID;

@Component
public class EmployeeCodeGenerator {

    public String generateEmployeeCode(User user) {
        // Role initials
        String rolePart = (user.getJobTitle() != null && user.getJobTitle().length() >= 2)
                ? user.getJobTitle().substring(0, 2).toUpperCase()
                : "XX";

        // Department initials from enum
        String deptPart = (user.getDepartment() != null)
                ? user.getDepartment().name().substring(0, 2).toUpperCase()
                : "XX";

        // Year part
        String yearPart = String.valueOf(Year.now().getValue()).substring(2);

        // Random part
        String randomPart = UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        return rolePart + deptPart + yearPart + randomPart;
    }

    // ⚠️ AJOUTER CETTE MÉTHODE POUR UserService
    public String generateEmployeeCode(String jobTitle, Department department) {
        // Role initials
        String rolePart = (jobTitle != null && jobTitle.length() >= 2)
                ? jobTitle.substring(0, 2).toUpperCase()
                : "XX";

        // Department initials from enum
        String deptPart = (department != null)
                ? department.name().substring(0, 2).toUpperCase()
                : "XX";

        // Year part
        String yearPart = String.valueOf(Year.now().getValue()).substring(2);

        // Random part
        String randomPart = UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        return rolePart + deptPart + yearPart + randomPart;
    }
}