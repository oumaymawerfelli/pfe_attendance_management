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

//@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

   /* private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    public void init() {
        try {
            System.out.println(" DataInitializer.init() STARTING ");

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

            System.out.println(" DataInitializer.init() COMPLETED SUCCESSFULLY ");
        } catch (Exception e) {
            System.err.println(" CRITICAL ERROR in DataInitializer: " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-throw to see full stack trace
        }
    }

    private void createDefaultRoles() {
        System.out.println(" Creating default roles...");

        for (RoleName roleName : RoleName.values()) {
            try {
                System.out.println(" Checking role: " + roleName);
                boolean exists = roleRepository.existsByName(roleName);
                System.out.println(" Role " + roleName + " exists? " + exists);

                if (!exists) {
                    Role role = new Role();
                    role.setName(roleName);

                    // Set description based on role
                    switch (roleName) {
                        case EMPLOYEE -> role.setDescription("Default employee role");
                        case PROJECT_MANAGER -> role.setDescription("Manages projects");
                        case ADMIN -> role.setDescription("Administrator with full access");
                        case GENERAL_MANAGER -> role.setDescription("General manager with all privileges");
                        default -> role.setDescription("System role");
                    }

                    roleRepository.save(role);
                    System.out.println("Created role: " + roleName);
                }
            } catch (Exception e) {
                System.err.println(" ERROR creating role " + roleName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void createAdminUser() {
        try {
            System.out.println(" Creating admin user...");

            // First, make sure ADMIN role exists
            Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                    .orElseGet(() -> {
                        System.out.println("⚠ ADMIN role not found, creating it...");
                        Role role = new Role();
                        role.setName(RoleName.ADMIN);
                        role.setDescription("Administrator with full access");
                        return roleRepository.save(role);
                    });

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
            admin.setRoles(Collections.singletonList(adminRole));

            userRepository.save(admin);
            System.out.println(" Created admin user: admin / Admin@123");
        } catch (Exception e) {
            System.err.println(" ERROR creating admin user: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTestUser() {
        try {
            System.out.println(" Creating test user...");

            // First, make sure EMPLOYEE role exists
            Role employeeRole = roleRepository.findByName(RoleName.EMPLOYEE)
                    .orElseGet(() -> {
                        System.out.println("⚠ EMPLOYEE role not found, creating it...");
                        Role role = new Role();
                        role.setName(RoleName.EMPLOYEE);
                        role.setDescription("Default employee role");
                        return roleRepository.save(role);
                    });

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
            testUser.setRoles(Collections.singletonList(employeeRole));

            userRepository.save(testUser);
            System.out.println(" Created test user: test / Test@123");
        } catch (Exception e) {
            System.err.println(" ERROR creating test user: " + e.getMessage());
            e.printStackTrace();
        }
    }*/
}