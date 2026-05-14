-- ============================================
-- Script d'initialisation de la DB pfe
-- Crée les rôles, l'utilisateur admin et les configs
-- ============================================

USE pfe;

-- ============================================
-- 1. INSÉRER LES RÔLES
-- ============================================
INSERT IGNORE INTO role_employee (name, description) VALUES
('ADMIN', 'Administrateur système avec tous les droits'),
('GENERAL_MANAGER', 'Directeur général'),
('PROJECT_MANAGER', 'Chef de projet'),
('EMPLOYEE', 'Employé standard');

-- ============================================
-- 2. CRÉER L'UTILISATEUR ADMIN
-- Email: Werfellioumayma15@gmail.com
-- Password: Admin@12345678 (hashé en BCrypt)
-- ============================================
INSERT INTO user_employe (
    account_non_expired,
    account_non_locked,
    active,
    credentials_non_expired,
    enabled,
    first_login,
    registration_pending,
    email,
    username,
    first_name,
    last_name,
    password_hash,
    hire_date,
    department,
    job_title
) VALUES (
             1,
             1,
             1,
             1,
             1,
             0,
             0,
             'Werfellioumayma15@gmail.com',
             'admin',
             'Oumayma',
             'Werfelli',
             '$2a$10$N3pMBYKZJv7ZlxqOyhVEXuuBBlNsBnL5XQvQ1bGPWE7EZSx8jEPsK',
             CURDATE(),
             'IT',
             'System Administrator'
         );

-- ============================================
-- 3. ASSOCIER L'UTILISATEUR AU RÔLE ADMIN
-- ============================================
INSERT INTO user_roles (user_id, role_id)
SELECT
    (SELECT id_employe FROM user_employe WHERE email = 'Werfellioumayma15@gmail.com'),
    (SELECT id_role FROM role_employee WHERE name = 'ADMIN');

-- ============================================
-- 4. INSÉRER LES CONFIGS DE PRÉSENCE
-- ============================================
INSERT IGNORE INTO attendance_config (config_key, config_value, description, last_modified_at, last_modified_by) VALUES
('WORK_START_HOUR', 8, 'Heure de début de travail', NOW(), 'system'),
('WORK_END_HOUR', 17, 'Heure de fin de travail', NOW(), 'system'),
('LATE_THRESHOLD_MINUTES', 15, 'Seuil de retard en minutes', NOW(), 'system'),
('DAILY_WORK_HOURS', 8, 'Nombre d''heures de travail par jour', NOW(), 'system'),
('WEEKLY_WORK_HOURS', 40, 'Nombre d''heures de travail par semaine', NOW(), 'system'),
('LUNCH_BREAK_MINUTES', 60, 'Durée de la pause déjeuner en minutes', NOW(), 'system'),
('OVERTIME_THRESHOLD_HOURS', 8, 'Seuil pour les heures supplémentaires', NOW(), 'system'),
('ANNUAL_LEAVE_DAYS', 30, 'Jours de congés annuels', NOW(), 'system'),
('SICK_LEAVE_DAYS', 15, 'Jours de congé maladie', NOW(), 'system');

-- ============================================
-- VÉRIFICATIONS
-- ============================================
SELECT '=== RÔLES ===' AS '';
SELECT * FROM role_employee;

SELECT '=== UTILISATEURS ===' AS '';
SELECT id_employe, email, username, first_name, last_name, enabled, active
FROM user_employe;

SELECT '=== ASSOCIATIONS USER-ROLE ===' AS '';
SELECT u.email, r.name AS role
FROM user_employe u
         JOIN user_roles ur ON u.id_employe = ur.user_id
         JOIN role_employee r ON ur.role_id = r.id_role;

SELECT '=== CONFIGS ===' AS '';
SELECT config_key, config_value FROM attendance_config;