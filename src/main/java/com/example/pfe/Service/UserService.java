package com.example.pfe.Service;

import com.example.pfe.dto.UserRequestDTO;
import com.example.pfe.dto.UserResponseDTO;
import com.example.pfe.entities.Role;
import com.example.pfe.entities.User;
import com.example.pfe.enums.RoleName;
import com.example.pfe.Repository.RoleRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final JwtService jwtService;

    // ==================== CRUD OPERATIONS ====================

    /**
     * Créer un nouvel utilisateur (pour admin)
     */
    public UserResponseDTO createUser(UserRequestDTO userRequestDTO) {
        log.info("Creating a new user: {}", userRequestDTO.getEmail());

        // 1. Validation
        validateUserCreation(userRequestDTO);

        // 2. Générer mot de passe temporaire
        String temporaryPassword = generateTemporaryPassword();

        // 3. Créer l'entité
        User user = buildUserEntity(userRequestDTO, temporaryPassword);

        // 5. Assigner les rôles
        assignRoles(user, userRequestDTO);

        // 6. Gérer les relations
        setUserRelations(user, userRequestDTO);

        // 4. Générer token d'activation
        String activationToken = generateActivationToken(user);
        user.setActivationToken(activationToken);
        user.setActivationTokenExpiry(LocalDateTime.now().plusDays(7));

        // 5. Sauvegarder
        user = userRepository.save(user);

        // 6. Envoyer email d'activation
        sendActivationEmail(user, temporaryPassword);

        log.info("User created successfully: {} (ID: {})", user.getEmail(), user.getId());
        return userMapper.toResponseDTO(user);
    }

    /**
     * Mettre à jour un utilisateur
     */
    public UserResponseDTO updateUser(Long id, UserRequestDTO userRequestDTO) {
        log.info("Updating user ID: {}", id);

        User existingUser = getUserEntityById(id);

        // Mapper les champs
        userMapper.updateEntityFromDTO(userRequestDTO, existingUser);

        // Mettre à jour les rôles si nécessaire
        if (userRequestDTO.getRoleNames() != null) {
            assignRoles(existingUser, userRequestDTO);
        }

        // Mettre à jour les relations
        setUserRelations(existingUser, userRequestDTO);

        User updatedUser = userRepository.save(existingUser);
        log.info("User updated successfully: {} (ID: {})", updatedUser.getEmail(), updatedUser.getId());

        return userMapper.toResponseDTO(updatedUser);
    }

    /**
     * Récupérer un utilisateur par ID
     */
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long id) {
        User user = getUserEntityById(id);
        return userMapper.toResponseDTO(user);
    }

    /**
     * Récupérer tous les utilisateurs (avec pagination)
     */
    @Transactional(readOnly = true)
    public Page<UserResponseDTO> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toResponseDTO);
    }



    // ==================== BUSINESS OPERATIONS ====================

    /**
     * Réactiver un utilisateur
     */
    public void reactivateUser(Long id) {
        User user = getUserEntityById(id);
        user.setActive(true);
        user.setEnabled(true);
        userRepository.save(user);
        log.info("User reactivated: {}", user.getEmail());
    }

    /**
     * Réinitialiser le mot de passe (admin)
     */
    public void resetUserPassword(Long id) {
        User user = getUserEntityById(id);

        String newTemporaryPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(newTemporaryPassword));
        user.setFirstLogin(true);
        userRepository.save(user);

        // Envoyer email avec nouveau mot de passe
        emailService.sendPasswordResetEmail(
                user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                newTemporaryPassword
        );

        log.info("Password reset for user: {}", user.getEmail());
    }

    /**
     * Rechercher des utilisateurs
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> searchUsers(String keyword, String department, Boolean active) {
        // Utilisez la méthode qui accepte String comme département
        return userRepository.searchUsersWithStringDepartment(keyword, department, active).stream()
                .map(userMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Récupérer les managers disponibles
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAvailableManagers() {
        return userRepository.findAvailableManagers().stream()
                .map(userMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private User getUserEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + id + " not found"));
    }

    private void validateUserCreation(UserRequestDTO userRequestDTO) {
        if (userRepository.existsByEmail(userRequestDTO.getEmail())) {
            throw new BusinessException("Email already exists");
        }
        if (userRepository.existsByNationalId(userRequestDTO.getNationalId())) {
            throw new BusinessException("National ID already exists");
        }
    }

    private String generateTemporaryPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        Random rnd = new Random();
        return rnd.ints(12, 0, chars.length())
                .mapToObj(i -> String.valueOf(chars.charAt(i)))
                .collect(Collectors.joining());
    }

    private User buildUserEntity(UserRequestDTO dto, String tempPassword) {
        User user = userMapper.toEntity(dto);

        // Use professional email as username
        user.setUsername(dto.getEmail());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setEnabled(false);
        user.setFirstLogin(true);
        user.setActive(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        return user;
    }

    private void assignRoles(User user, UserRequestDTO dto) {
        List<Role> roles = new ArrayList<>();

        if (dto.getRoleNames() != null && !dto.getRoleNames().isEmpty()) {
            for (String roleName : dto.getRoleNames()) {
                try {
                    RoleName roleNameEnum = RoleName.valueOf(roleName.toUpperCase());
                    Role role = roleRepository.findByName(roleNameEnum)
                            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));

                    // Éviter les doublons
                    if (roles.stream().noneMatch(r -> r.getId().equals(role.getId()))) {
                        roles.add(role);
                    }
                } catch (IllegalArgumentException e) {
                    throw new BusinessException("Invalid role name: " + roleName);
                }
            }
        } else {
            // Rôle par défaut
            Role defaultRole = roleRepository.findByName(RoleName.EMPLOYEE)
                    .orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE role not found"));
            roles.add(defaultRole);
        }

        user.setRoles(roles);
    }

    private void setUserRelations(User user, UserRequestDTO dto) {
        if (dto.getDirectManagerId() != null) {
            User manager = userRepository.findById(dto.getDirectManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Direct manager not found"));
            user.setDirectManager(manager);
        }

        if (dto.getAssignedProjectManagerId() != null) {
            User pm = userRepository.findById(dto.getAssignedProjectManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project manager not found"));
            user.setAssignedProjectManager(pm);
        }
    }

    private String generateActivationToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("type", "ACCOUNT_ACTIVATION");

        return jwtService.generateTokenWithExpiration(
                claims,
                user.getEmail(),
                7 * 24 * 60 * 60 * 1000L // 7 jours
        );
    }

    private void sendActivationEmail(User user, String tempPassword) {
        try {
            emailService.sendWelcomeEmail(
                    user.getEmail(),
                    user.getFirstName() + " " + user.getLastName(),
                    tempPassword,
                    user.getActivationToken()
            );
        } catch (Exception e) {
            log.error("Failed to send activation email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private boolean shouldRegenerateEmployeeCode(User existingUser, UserRequestDTO newData) {
        return !Objects.equals(existingUser.getDepartment(), newData.getDepartment()) ||
                !Objects.equals(existingUser.getJobTitle(), newData.getJobTitle());
    }
}