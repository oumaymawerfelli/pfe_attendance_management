package com.example.pfe.Service;

import com.example.pfe.dto.UserRequestDTO;
import com.example.pfe.dto.UserResponseDTO;
import com.example.pfe.dto.UserStatsDTO;
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
        user.setUsername(dto.getEmail());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setEnabled(false);           // Not registered yet
        user.setActive(false);            // ← CHANGED: was true, now false (no login until approved)
        user.setFirstLogin(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        user.setRegistrationPending(true);
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



    // File: src/main/java/com/example/pfe/Service/UserService.java
// Add these methods

    // ==================== STATUS MANAGEMENT METHODS ====================

    /**
     * Disable a user
     */
    public void disableUser(Long id) {
        log.info("Disabling user ID: {}", id);
        User user = getUserEntityById(id);

        if (!user.getActive()) {
            throw new BusinessException("User is already disabled");
        }
        // ← Only touch active, never enabled
        user.setActive(false);
        userRepository.save(user);

        try {
            emailService.sendAccountDisabledEmail(user.getEmail(),
                    user.getFirstName() + " " + user.getLastName());
        } catch (Exception e) {
            log.error("Failed to send disable email: {}", e.getMessage());
        }
        log.info("User disabled successfully: {}", user.getEmail());
    }
    /**
     * Approve a pending user (for registration approval)
     */
    public void approveUser(Long id) {
        log.info("Approving user ID: {}", id);
        User user = getUserEntityById(id);

        if (user.isEnabled()) {
            throw new BusinessException("User is already approved");
        }
        // ← Both flags set on approval
        user.setEnabled(true);            // Marks registration as complete
        user.setActive(true);             // Grants login access
        user.setRegistrationPending(false);
        user.setAccountNonLocked(true);
        user.setActivationToken(null);
        user.setActivationTokenExpiry(null);
        userRepository.save(user);

        try {
            emailService.sendAccountApprovedEmail(user.getEmail(),
                    user.getFirstName() + " " + user.getLastName());
        } catch (Exception e) {
            log.error("Failed to send approval email: {}", e.getMessage());
        }
        log.info("User approved successfully: {}", user.getEmail());
    }

    /**
     * Reject a pending user
     */
    public void rejectUser(Long id) {
        log.info("Rejecting user ID: {}", id);

        User user = getUserEntityById(id);

        if (user.isEnabled()) {
            throw new BusinessException("Cannot reject an approved user");
        }

        // Delete the user (or you could mark them as rejected)
        userRepository.delete(user);

        log.info("User rejected successfully: {}", user.getEmail());
    }

    /**
     * Get user statistics
     */
    public UserStatsDTO getUserStats() {
        return UserStatsDTO.builder()
                .pending(userRepository.countPendingUsers())     // registrationPending=true
                .active(userRepository.countActiveUsers())       // active=true AND enabled=true
                .disabled(userRepository.countDisabledUsers())   // enabled=true BUT active=false
                .locked(userRepository.countLockedUsers())       // accountNonLocked=false
                .total(userRepository.count())
                .build();
    }
    /**
     * Get users by status with pagination
     */
    public Page<UserResponseDTO> getUsersByStatus(String status, Pageable pageable) {
        log.info("Getting users by status: {}", status);

        Page<User> usersPage = userRepository.findByStatus(status, pageable);
        return usersPage.map(userMapper::toResponseDTO);
    }
    /**
     * Fix all users to have correct status flags

    @Transactional
    public void fixAllUserStatus() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            // Set accountNonLocked to true for all normal users
            user.setAccountNonLocked(true);

            // If user is approved, set these
            if (user.isEnabled()) {
                user.setActive(true);
                user.setRegistrationPending(false);
            }

            log.info("Fixed user: {} - accountNonLocked={}, active={}, enabled={}, registrationPending={}",
                    user.getEmail(), user.isAccountNonLocked(), user.getActive(), user.isEnabled(), user.isRegistrationPending());
        }
        userRepository.saveAll(users);
    }*/

    public void enableUser(Long id) {
        log.info("Enabling user ID: {}", id);
        User user = getUserEntityById(id);

        if (!user.isEnabled()) {
            // enabled=false means they were never approved — they can't be re-enabled this way
            throw new BusinessException("User has not completed registration. Use approve instead.");
        }
        if (user.getActive()) {
            throw new BusinessException("User is already active");
        }
        // ← Only restore active, registration (enabled) stays untouched
        user.setActive(true);
        userRepository.save(user);
        log.info("User re-enabled successfully: {}", user.getEmail());
    }





    @Transactional
    public void fixAllUserStatus() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            user.setAccountNonLocked(true); // ← Fix the root cause of "Locked"

            if (user.isEnabled()) {
                // Already approved users
                user.setActive(true);
                user.setRegistrationPending(false);
            } else {
                // Never approved — set as pending
                user.setActive(false);
                user.setRegistrationPending(true);
            }
        }
        userRepository.saveAll(users);
        log.info("Fixed {} users", users.size());
    }
    /**
     * Rechercher des utilisateurs avec pagination
     */
    @Transactional(readOnly = true)
    public Page<UserResponseDTO> searchUsers(String keyword, Pageable pageable) {
        log.info("Searching users with keyword: {}", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllUsers(pageable);
        }

        Page<User> usersPage = userRepository.searchByKeyword(keyword.trim(), pageable);
        return usersPage.map(userMapper::toResponseDTO);
    }

}