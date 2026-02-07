package com.example.pfe.Service;

import com.example.pfe.dto.*;
import com.example.pfe.entities.User;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.UserMapper;
import com.example.pfe.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final UserMapper userMapper;

    // ==================== LOGIN ====================

    public JwtResponseDTO authenticate(LoginRequestDTO request) {
        User user = validateCredentials(request);
        checkAccountStatus(user);

        String accessToken = jwtService.generateAccessToken(user);

        return JwtResponseDTO.builder()
                .token(accessToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .message("Login successful")
                .user(userMapper.toResponseDTO(user))
                .build();
    }

    // ==================== ACCOUNT ACTIVATION ====================

    /**
     * Activer un compte
     */
    public JwtResponseDTO activateAccount(ActivationRequestDTO request) {
        // Validation des mots de passe
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        // Validation du token
        User user = getValidUserFromActivationToken(request.getToken());

        // Validation du nom d'utilisateur
        if (!request.getUsername().equals(user.getEmployeeCode()) &&
                request.getUsername().length() < 3) {
            throw new BusinessException("Username must be at least 3 characters");
        }

        // Activation du compte
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setEnabled(true);
        user.setFirstLogin(false);
        user.setActivationToken(null);
        user.setActivationTokenExpiry(null);

        userRepository.save(user);

        // Générer le token d'accès
        String accessToken = jwtService.generateAccessToken(user);

        return JwtResponseDTO.builder()
                .token(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getRemainingTime(accessToken) / 1000)
                .message("Account activated successfully")
                .build();
    }

    /**
     * Valider un token d'activation (pour l'API publique)
     */
    public boolean validateActivationTokenApi(String token) {
        try {
            User user = getValidUserFromActivationToken(token);
            return user != null && !user.isEnabled()
                    && user.getActivationTokenExpiry().isAfter(LocalDateTime.now());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Réenvoyer l'email d'activation
     */
    public ResendActivationResponseDTO resendActivationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isEnabled()) {
            throw new BusinessException("Account already activated");
        }

        // Régénérer le token
        String token = jwtService.generateActivationToken(user);
        user.setActivationToken(token);
        user.setActivationTokenExpiry(LocalDateTime.now().plusDays(7));
        userRepository.save(user);

        // Réenvoyer l'email en utilisant la méthode avec 2 paramètres
        emailService.sendActivationReminderEmail(user.getEmail(), token);

        return ResendActivationResponseDTO.builder()
                .email(email)
                .message("Activation email resent")
                .build();
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private User validateCredentials(LoginRequestDTO request) {
        User user = userRepository.findByUsernameOrEmail(
                        request.getUsername(),
                        request.getUsername()
                )
                .orElseThrow(() -> new BusinessException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid credentials");
        }

        return user;
    }

    private void checkAccountStatus(User user) {
        if (!user.isEnabled()) {
            throw new BusinessException("Account not activated. Please check your email.");
        }
        if (user.isFirstLogin()) {
            throw new BusinessException("Password change required on first login");
        }
    }

    /**
     * Récupérer un utilisateur depuis un token d'activation valide
     * (MÉTHODE PRIVÉE pour usage interne)
     */
    private User getValidUserFromActivationToken(String token) {
        if (!jwtService.isTokenValid(token) || !jwtService.isActivationToken(token)) {
            throw new BusinessException("Invalid or expired activation token");
        }

        User user = userRepository.findByActivationToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isEnabled()) {
            throw new BusinessException("Account already activated");
        }

        if (user.getActivationTokenExpiry() == null ||
                user.getActivationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Activation token expired");
        }

        return user;
    }
}