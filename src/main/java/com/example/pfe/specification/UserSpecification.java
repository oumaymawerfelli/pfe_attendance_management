package com.example.pfe.specification;
import com.example.pfe.entities.User;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable JPA specs for the user list page.
 * All params are optional — null = ignored.
 */
public class UserSpecification {

    public static Specification<User> build(String keyword,
                                            String department,
                                            String status) {
        return Specification
                .where(byKeyword(keyword))
                .and(byDepartment(department))
                .and(byStatus(status));
    }

    // ── keyword: partial match on firstName, lastName, or email ──
    private static Specification<User> byKeyword(String keyword) {
        return (root, q, cb) -> {
            if (keyword == null || keyword.isBlank()) return null;
            String p = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), p),
                    cb.like(cb.lower(root.get("lastName")),  p),
                    cb.like(cb.lower(root.get("email")),     p)
            );
        };
    }

    // ── department: exact enum match ──────────────────────────────
    private static Specification<User> byDepartment(String department) {
        return (root, q, cb) -> {
            if (department == null || department.isBlank()) return null;
            try {
                var dept = com.example.pfe.enums.Department.valueOf(department.toUpperCase());
                return cb.equal(root.get("department"), dept);
            } catch (IllegalArgumentException e) {
                return null; // unknown value → ignore filter
            }
        };
    }

    // ── status: maps string to the boolean flag combination ───────
    private static Specification<User> byStatus(String status) {
        return (root, q, cb) -> {
            if (status == null || status.isBlank()) return null;

            return switch (status.toUpperCase()) {
                case "ACTIVE"   -> cb.and(
                        cb.isTrue(root.get("enabled")),
                        cb.isTrue(root.get("active")),
                        cb.isFalse(root.get("registrationPending"))
                );
                case "PENDING"  -> cb.or(
                        cb.isTrue(root.get("registrationPending")),
                        cb.isFalse(root.get("enabled"))
                );
                case "DISABLED" -> cb.and(
                        cb.isTrue(root.get("enabled")),
                        cb.isFalse(root.get("active"))
                );
                case "LOCKED"   -> cb.isFalse(root.get("accountNonLocked"));
                default         -> null;
            };
        };
    }

    public static Specification<User> build(String keyword,
                                            String department,
                                            String status,
                                            String role) {      // ← add role param
        return Specification
                .where(byKeyword(keyword))
                .and(byDepartment(department))
                .and(byStatus(status))
                .and(byRole(role));                                  // ← add this
    }

    // Add this predicate:
    private static Specification<User> byRole(String role) {
        return (root, q, cb) -> {
            if (role == null || role.isBlank()) return null;
            try {
                // Frontend sends "GENERAL MANAGER" → convert to enum "GENERAL_MANAGER"
                String enumName = role.toUpperCase().replace(" ", "_");
                var roleEnum = com.example.pfe.enums.RoleName.valueOf(enumName);
                var rolesJoin = root.join("roles", jakarta.persistence.criteria.JoinType.INNER);
                q.distinct(true); // prevent duplicate rows from the JOIN
                return cb.equal(rolesJoin.get("name"), roleEnum);
            } catch (IllegalArgumentException e) {
                return null; // unknown role value → ignore filter
            }
        };
    }
}

