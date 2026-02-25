// File: src/main/java/com/example/pfe/controller/UserController.java
package com.example.pfe.Controller;

import com.example.pfe.Service.UserService;
import com.example.pfe.dto.UserRequestDTO;
import com.example.pfe.dto.UserResponseDTO;
import com.example.pfe.dto.UserStatsDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER')")
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserRequestDTO request) {
        UserResponseDTO response = userService.createUser(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        UserResponseDTO response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }



    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserRequestDTO request) {
        UserResponseDTO response = userService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }

    /*@PostMapping("/fix-status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER')")
    public ResponseEntity<String> fixAllUserStatus() {
        userService.fixAllUserStatus();
        return ResponseEntity.ok("All users status fixed");
    }
*/
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id) {
        userService.resetUserPassword(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivateUser(@PathVariable Long id) {
        userService.reactivateUser(id);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER') or hasRole('HR_MANAGER')")
    public ResponseEntity<UserStatsDTO> getUserStats() {
        UserStatsDTO stats = userService.getUserStats();
        return ResponseEntity.ok(stats);
    }
    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enableUser(@PathVariable Long id) {
        userService.enableUser(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disableUser(@PathVariable Long id) {
        userService.disableUser(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approveUser(@PathVariable Long id) {
        userService.approveUser(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> rejectUser(@PathVariable Long id) {
        userService.rejectUser(id);
        return ResponseEntity.ok().build();
    }

    // UserController.java — temporary migration endpoint
    @PostMapping("/fix-status")
    public ResponseEntity<String> fixStatus() {
        userService.fixAllUserStatus();
        return ResponseEntity.ok("Fixed");
    }

    @GetMapping
    public ResponseEntity<Page<UserResponseDTO>> getAllUsers(
            @PageableDefault(size = 10) Pageable pageable,
            @RequestParam(required = false) String search) {

        Page<UserResponseDTO> response;

        if (search != null && !search.trim().isEmpty()) {
            // Recherche avec terme
            response = userService.searchUsers(search.trim(), pageable);

        } else {
            // Tous les utilisateurs
            response = userService.getAllUsers(pageable);
        }

        return ResponseEntity.ok(response);
    }
    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadUserPhoto(
            @PathVariable Long id,
            @RequestParam("photo") MultipartFile photo) {

        String avatarUrl = userService.uploadUserPhoto(id, photo);

        return ResponseEntity.ok(Map.of(
                "avatarUrl", avatarUrl,
                "message", "Photo uploaded successfully"
        ));
    }
}