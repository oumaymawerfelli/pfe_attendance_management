package com.example.pfe.Controller;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.AuthenticationService;
import com.example.pfe.Service.JwtService;
import com.example.pfe.dto.*;
import com.example.pfe.entities.User;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated

public class AuthController {

    private final AuthenticationService authenticationService;

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        JwtResponseDTO response = authenticationService.authenticate(request);
        return ResponseEntity.ok(response);
    }










    @PostMapping("/activate")
    public ResponseEntity<JwtResponseDTO> activateAccount(
            @Valid @RequestBody ActivationRequestDTO request) {
        JwtResponseDTO response = authenticationService.activateAccount(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate-activation-token/{token}")
    public ResponseEntity<Map<String, Object>> validateActivationToken(@PathVariable String token) {
        boolean isValid = authenticationService.validateActivationTokenApi(token);

        if (isValid) {
            // Extraire l'ID utilisateur du token
            Long userId = jwtService.extractUserId(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("employeeCode", user.getEmployeeCode());
            response.put("email", user.getEmail());
            response.put("firstName", user.getFirstName());
            response.put("lastName", user.getLastName());

            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("valid", false, "message", "Invalid or expired token"));
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
}