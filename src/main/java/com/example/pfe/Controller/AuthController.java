package com.example.pfe.Controller;


import com.example.pfe.dto.ChangePasswordDTO;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.AuthenticationService;
import com.example.pfe.Service.JwtService;
import com.example.pfe.Service.TokenBlacklistService;
import com.example.pfe.Service.UserService;
import com.example.pfe.dto.*;
import com.example.pfe.entities.User;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.pfe.Service.UserService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
@RequiredArgsConstructor
@Validated

public class AuthController {

    private final AuthenticationService authenticationService;
    private final TokenBlacklistService blacklistService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserService userService;


    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        JwtResponseDTO response = authenticationService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO request) {
        try {
            // Call service to register user
            RegistrationResponseDTO response = authenticationService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Registration failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Registration failed. Please try again."));
        }
    }

    // ==================== REGISTRATION APPROVAL (ADMIN / GENERAL MANAGER) ====================

    /**
     * List all users that self‑registered and are waiting for approval.
     */
    @GetMapping("/pending-registrations")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER')")
    public ResponseEntity<java.util.List<RegistrationResponseDTO>> getPendingRegistrations() {
        java.util.List<RegistrationResponseDTO> pending = authenticationService.getPendingRegistrations();
        return ResponseEntity.ok(pending);
    }

    /**
     * Approve a self‑registration. After approval, an activation email is sent.
     */
    @PostMapping("/approve-registration/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER')")
    public ResponseEntity<?> approveRegistration(@PathVariable Long userId) {
        try {
            RegistrationResponseDTO response = authenticationService.approveRegistration(userId);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error approving registration for user {}: ", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to approve registration"));
        }
    }


    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponseDTO> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return ResponseEntity.ok(userMapper.toResponseDTO(user));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            blacklistService.blacklist(token); // Ajoute à la blacklist
        }

        return ResponseEntity.ok(Map.of("message", "Déconnecté"));
    }






    @PostMapping("/activate")
    public ResponseEntity<JwtResponseDTO> activateAccount(
            @Valid @RequestBody ActivationRequestDTO request) {
        JwtResponseDTO response = authenticationService.activateAccount(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate-activation-token/{token}")
    public ResponseEntity<Map<String, Object>> validateActivationToken(@PathVariable String token) {
        try {
            // First validate the token
            boolean isValid = authenticationService.validateActivationTokenApi(token);

            if (!isValid) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("valid", false, "message", "Invalid or expired token"));
            }

            // Find user by activation token directly from database
            User user = userRepository.findByActivationToken(token)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with this token"));

            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("email", user.getEmail());
            response.put("firstName", user.getFirstName());
            response.put("lastName", user.getLastName());
            response.put("userId", user.getId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error validating activation token: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("valid", false, "message", "Error validating token"));
        }
    }
    @PostMapping("/resend-activation")
    public ResponseEntity<?> resendActivationEmail(
            @RequestParam String email) {
        try {
            ResendActivationResponseDTO response = authenticationService.resendActivationEmail(email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }


    @GetMapping("/me/menu")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> getUserMenu(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<Map<String, Object>> menu = new ArrayList<>();
        boolean isAdmin = hasRole(userDetails, "ROLE_ADMIN");
        boolean isGM    = hasRole(userDetails, "ROLE_GENERAL_MANAGER");
        boolean isPM    = hasRole(userDetails, "ROLE_PROJECT_MANAGER");
        boolean isManager = isAdmin || isGM || isPM;

        menu.add(link("Dashboard", "dashboard", "dashboard"));

        List<Map<String, Object>> attendanceChildren = new ArrayList<>();
        attendanceChildren.add(child("My Summary", "summary", "link"));
        attendanceChildren.add(child("History",    "history", "link"));
        if (isManager) attendanceChildren.add(child("Manage All", "all", "link"));
        menu.add(sub("Attendance", "attendance", "timer", attendanceChildren));

        List<Map<String, Object>> leaveChildren = new ArrayList<>();
        leaveChildren.add(child("My Leaves", "", "link"));
        if (isManager) leaveChildren.add(child("Manage Leaves", "all", "link"));
        menu.add(sub("Leave", "leave", "event_busy", leaveChildren));

        menu.add(link("Notifications", "notifications", "notifications"));

        if (isAdmin || isGM) {
            menu.add(link("Users",    "users",    "people"));
            menu.add(link("Projects", "projects", "assignment"));
        }

        menu.add(link("Profile", "profile", "person"));

        return ResponseEntity.ok(menu);
    }

    private boolean hasRole(UserDetails user, String role) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    private Map<String, Object> link(String name, String route, String icon) {
        Map<String, Object> item = new HashMap<>();
        item.put("route", route);
        item.put("name",  name);
        item.put("type",  "link");
        item.put("icon",  icon);
        return item;
    }

    private Map<String, Object> sub(String name, String route, String icon,
                                    List<Map<String, Object>> children) {
        Map<String, Object> item = new HashMap<>();
        item.put("route",    route);
        item.put("name",     name);
        item.put("type",     "sub");
        item.put("icon",     icon);
        item.put("children", children);
        return item;
    }

    private Map<String, Object> child(String name, String route, String type) {
        Map<String, Object> item = new HashMap<>();
        item.put("route", route);
        item.put("name",  name);
        item.put("type",  type);
        return item;
    }


    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordDTO request) {
        try {
            userService.changePassword(userDetails.getUsername(), request);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error changing password: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to change password"));
        }
    }

}