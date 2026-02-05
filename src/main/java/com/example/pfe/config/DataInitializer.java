package com.example.pfe.config;

import com.example.pfe.Repository.RoleRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.entities.Role;
import com.example.pfe.entities.User;
import com.example.pfe.enums.RoleName;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        // Create default roles if they don't exist
        createDefaultRoles();

        // Create admin user if not exists
        if (!userRepository.existsByEmail("admin@company.com")) {
            createAdminUser();
        }

        // Create test user for Swagger
        if (!userRepository.existsByEmail("test@company.com")) {
            createTestUser();
        }
    }

    private void createDefaultRoles() {
        for (RoleName roleName : RoleName.values()) {
            if (!roleRepository.existsByName(roleName)) {
                Role role = new Role();
                role.setName(roleName);
                roleRepository.save(role);
                log.info("Created role: {}", roleName);
            }
        }
    }

    private void createAdminUser() {
        User admin = new User();
        admin.setFirstName("Admin");
        admin.setLastName("System");
        admin.setEmail("admin@company.com");
        admin.setUsername("admin");
        admin.setEmployeeCode("ADM001");
        admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
        admin.setEnabled(true);
        admin.setFirstLogin(false);
        admin.setActive(true);

        // Assign ADMIN role
        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new RuntimeException("ADMIN role not found"));
        admin.setRoles(Collections.singletonList(adminRole));

        userRepository.save(admin);
        log.info("Created admin user: admin / Admin@123");
    }

    private void createTestUser() {
        User testUser = new User();
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setEmail("test@company.com");
        testUser.setUsername("test");
        testUser.setEmployeeCode("TEST001");
        testUser.setPasswordHash(passwordEncoder.encode("Test@123"));
        testUser.setEnabled(true);
        testUser.setFirstLogin(false);
        testUser.setActive(true);

        // Assign EMPLOYEE role
        Role employeeRole = roleRepository.findByName(RoleName.EmPLOYEE)
                .orElseThrow(() -> new RuntimeException("EMPLOYEE role not found"));
        testUser.setRoles(Collections.singletonList(employeeRole));

        userRepository.save(testUser);
        log.info("Created test user: test / Test@123");
    }
}