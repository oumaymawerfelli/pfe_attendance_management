package com.example.pfe.Service;

import com.example.pfe.dto.ActivationRequestDTO;
import com.example.pfe.dto.JwtResponseDTO;
import com.example.pfe.dto.ResendActivationResponseDTO;
import com.example.pfe.entities.User;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
//A user received an activation email → sets username & password → account becomes active → auto login
public class ActivationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    public JwtResponseDTO activateAccount(ActivationRequestDTO request) {
        // 1. Valider que les mots de passe correspondent
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        // 2. Vérifier et valider le token
        String token = request.getToken();
        if (!jwtService.isTokenValid(token)) {
            throw new BusinessException("Invalid or expired activation token");
        }

        // 3. Extraire l'ID utilisateur du token
        //Ensures it's an ACCOUNT_ACTIVATION token (not login token
        Long userId = jwtService.extractClaim(token, claims -> claims.get("userId", Long.class));
        String tokenType = jwtService.extractClaim(token, claims -> claims.get("type", String.class));

        if (!"ACCOUNT_ACTIVATION".equals(tokenType)) {
            throw new BusinessException("Invalid token type");
        }

        // 4. Trouver l'utilisateur
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 5. Vérifier que le token correspond
        //Even if JWT is valid → user could have requested a newer token
        //Prevents token replay attacks
        if (!token.equals(user.getActivationToken())) {
            throw new BusinessException("Invalid activation token");
        }

        // 6. Vérifier que le token n'est pas expiré
        //Why both JWT expiration and DB expiration?

        //JWT expiration → cryptographic
        //DB expiration → business control
        if (user.getActivationTokenExpiry() != null &&
                user.getActivationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Activation token has expired");
        }

        // 7. Handle username validation
        String requestedUsername = request.getUsername();
        String employeeCode = user.getEmployeeCode();

        if (requestedUsername.equals(employeeCode)) {
            // Username equals employee code → always allowed
            log.info("Username matches employee code: {}", employeeCode);
        } else {
            // Username is different → must be unique
            if (userRepository.existsByUsername(requestedUsername)) {
                throw new BusinessException("Username '" + requestedUsername + "' is already taken");
            }

            // EXTRA CHECK: Prevent username = someone else's employee code
            Optional<User> userWithSameEmployeeCode = userRepository.findByEmployeeCode(requestedUsername);
            if (userWithSameEmployeeCode.isPresent()) {
                throw new BusinessException("Username '" + requestedUsername + "' conflicts with an existing employee code");
            }
        }

// 8. Update user
        user.setUsername(requestedUsername); // Always set

        // 8. Mettre à jour l'utilisateur
        //This is the state transition:

        //INACTIVE → ACTIVE
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setEnabled(true);
        user.setFirstLogin(false);
        user.setActivationToken(null);
        user.setActivationTokenExpiry(null);

        user = userRepository.save(user);

        log.info("Account successfully activated for user: {}", user.getEmail());

        // 9. Générer un token JWT pour la connexion automatique
        String jwtToken = jwtService.generateToken(user);

        return JwtResponseDTO.builder()
                .token(jwtToken)
                .message("Account activated successfully")
                .build();
    }
    /******************************************************************************************************/
//Frontend wants to know if activation link is still valid
    //Why not reuse activateAccount?
    //This is read-only
    //No DB update
    public boolean validateActivationToken(String token) {
        try {
            if (!jwtService.isTokenValid(token)) {
                return false;
            }

            Long userId = jwtService.extractClaim(token, claims -> claims.get("userId", Long.class));
            String tokenType = jwtService.extractClaim(token, claims -> claims.get("type", String.class));
//This link must ONLY be for account activation.
            if (!"ACCOUNT_ACTIVATION".equals(tokenType)) {
                return false;
            }
//Does this user still exist?
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
//Throwing exceptions would:

//complicate frontend

//expose unnecessary error details
            return token.equals(user.getActivationToken()) &&
                    user.getActivationTokenExpiry() != null &&
                    user.getActivationTokenExpiry().isAfter(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error while validating activation token: {}", e.getMessage());
            return false;
        }
    }

    public ResendActivationResponseDTO resendActivationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with this email"));

        if (user.isEnabled()) {
            throw new BusinessException("This account is already activated");
        }

        // Régénérer le token
        Map<String, Object> claims = Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "type", "ACCOUNT_ACTIVATION"
        );

        String newToken = jwtService.generateTokenWithExpiration(
                claims,
                user.getEmail(),
                7 * 24 * 60 * 60 * 1000L
        );

        user.setActivationToken(newToken);
        user.setActivationTokenExpiry(LocalDateTime.now().plusDays(7));
        userRepository.save(user);

        // Renvoyer l'email
        emailService.sendWelcomeEmail(
                user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                user.getEmployeeCode(),
                "[Temporary password already sent]",
                newToken
        );

        return ResendActivationResponseDTO.builder()
                .message("Activation email resent successfully")
                .build();
    }
}