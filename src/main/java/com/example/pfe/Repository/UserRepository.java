package com.example.pfe.Repository;

import com.example.pfe.entities.User;
import com.example.pfe.enums.Department;
import com.example.pfe.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameOrEmail(String username, String email);
    boolean existsByEmail(String email);
    boolean existsByNationalId(String nationalId);
    boolean existsByEmployeeCode(String employeeCode);
    boolean existsByUsername(String username);

    // Ajoutez cette méthode pour trouver par token d'activation
    Optional<User> findByActivationToken(String activationToken);

    // Pour la recherche
    @Query("SELECT u FROM User u WHERE " +
            "(:keyword IS NULL OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "u.employeeCode LIKE CONCAT('%', :keyword, '%')) AND " +
            "(:department IS NULL OR u.department = :department) AND " +
            "(:active IS NULL OR u.active = :active)")
    List<User> searchUsers(@Param("keyword") String keyword,
                           @Param("department") Department department,
                           @Param("active") Boolean active);

    // Surcharge pour accepter String comme département
    @Query("SELECT u FROM User u WHERE " +
            "(:keyword IS NULL OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "u.employeeCode LIKE CONCAT('%', :keyword, '%')) AND " +
            "(:department IS NULL OR CAST(u.department AS string) = :department) AND " +
            "(:active IS NULL OR u.active = :active)")
    List<User> searchUsersWithStringDepartment(
            @Param("keyword") String keyword,
            @Param("department") String department,
            @Param("active") Boolean active);

    // Pour les managers
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name IN :roleNames")
    List<User> findByRoles_NameIn(@Param("roleNames") List<RoleName> roleNames);

    // Par département
    List<User> findByDepartment(Department department);

    Optional<User> findByEmployeeCode(String employeeCode);

    // Managers disponibles
    @Query("SELECT DISTINCT u FROM User u " +
            "JOIN u.roles r " +
            "WHERE r.name IN (com.example.pfe.enums.RoleName.GENERAL_MANAGER, " +
            "com.example.pfe.enums.RoleName.PROJECT_MANAGER) " +
            "AND u.active = true " +
            "AND u.enabled = true")
    List<User> findAvailableManagers();
}