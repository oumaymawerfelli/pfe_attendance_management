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
import java.util.UUID;

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

    // ==================== REGISTRATION ====================

    public RegistrationResponseDTO register(RegisterRequestDTO request) {
        log.info("Starting self-registration for email: {}", request.getEmail());

        // Validate DTO business rules
        request.validate();

        // Check for unique constraints
        validateUniqueConstraints(request);

        // Create user
        User user = createUserFromRequest(request);
        // Use professional email as unique username
        user.setUsername(request.getEmail());

        User savedUser = userRepository.save(user);
        log.info("User saved: ID={}, Email={}", savedUser.getId(), savedUser.getEmail());

        return buildRegistrationResponse(savedUser);
    }

    private void validateUniqueConstraints(RegisterRequestDTO request) {
        // Check email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered");
        }

        // Check national ID
        if (userRepository.existsByNationalId(request.getNationalId())) {
            throw new BusinessException("National ID already registered");
        }
    }

    private User createUserFromRequest(RegisterRequestDTO request) {
        User user = userMapper.toEntity(request);

        String tempPassword = UUID.randomUUID().toString().substring(0, 12);
        user.setPasswordHash(passwordEncoder.encode(tempPassword));

        user.setEnabled(false);
        user.setFirstLogin(true);
        user.setActive(request.getActive() != null ? request.getActive() : true);
        // Mark as waiting for admin / general manager approval
        user.setRegistrationPending(true);

        String activationToken = jwtService.generateActivationToken(user);
        user.setActivationToken(activationToken);
        user.setActivationTokenExpiry(LocalDateTime.now().plusDays(7));

        return user;
    }

    private RegistrationResponseDTO buildRegistrationResponse(User user) {
        return RegistrationResponseDTO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .active(user.getActive())
                .enabled(user.isEnabled())
                .message("Registration successful.")
                .activationEmailSent(false)
                .build();
    }

    // ==================== REGISTRATION APPROVAL (ADMIN / GENERAL MANAGER) ====================

    /**
     * List all users that self‑registered and are still waiting for approval.
     */
    @Transactional(readOnly = true)
    public java.util.List<RegistrationResponseDTO> getPendingRegistrations() {
        return userRepository.findByRegistrationPendingTrue()
                .stream()
                .map(this::buildRegistrationResponse)
                .peek(dto -> {
                    dto.setMessage("Waiting for approval");
                    dto.setActivationEmailSent(false);
                })
                .toList();
    }

    /**
     * Approve a pending self‑registration and send the activation email.
     */
    public RegistrationResponseDTO approveRegistration(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BusinessException("User is not active");
        }

        if (!user.isRegistrationPending()) {
            throw new BusinessException("User is not pending approval");
        }

        if (user.isEnabled()) {
            throw new BusinessException("Account already activated");
        }

        // (Re)generate activation token if missing or expired
        if (user.getActivationToken() == null ||
                user.getActivationTokenExpiry() == null ||
                user.getActivationTokenExpiry().isBefore(LocalDateTime.now())) {
            String token = jwtService.generateActivationToken(user);
            user.setActivationToken(token);
            user.setActivationTokenExpiry(LocalDateTime.now().plusDays(7));
        }

        // Mark as approved so we don't list it again
        user.setRegistrationPending(false);
        userRepository.save(user);

        // Send activation email after approval
        emailService.sendActivationReminderEmail(user.getEmail(), user.getActivationToken());

        RegistrationResponseDTO dto = buildRegistrationResponse(user);
        dto.setMessage("Registration approved. Activation email sent.");
        dto.setActivationEmailSent(true);
        return dto;
    }

    // ==================== LOGIN ====================

    public JwtResponseDTO authenticate(LoginRequestDTO request) {

        User user = validateCredentials(request);

        checkAccountStatus(user);

        String accessToken = jwtService.generateAccessToken(user);

        JwtResponseDTO response = JwtResponseDTO.builder()
                .token(accessToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .message("Login successful")
                .user(userMapper.toResponseDTO(user))
                .build();



        return response;
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

        // Validation du nom d'utilisateur (plus de employeeCode, on vérifie juste la longueur)
        if (request.getUsername() == null || request.getUsername().length() < 3) {
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
        // Now using email only (case-insensitive)
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
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