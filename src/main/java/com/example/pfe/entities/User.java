package com.example.pfe.entities;

import com.example.pfe.enums.ContractType;
import com.example.pfe.enums.Department;
import com.example.pfe.enums.Gender;
import com.example.pfe.enums.MaritalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "userEmploye")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   @Column(name = "idEmploye")
   private Long id;
   private String lastName;
   private String firstName;


   private LocalDate birthDate;
   private LocalDate hireDate;
   private LocalDate contractEndDate;
   private LocalDate evaluationDate;
   private LocalDate lastLogin;

   @Enumerated(EnumType.STRING)
   private Gender gender;

   private String nationalId;
   private String nationality;

   @Enumerated(EnumType.STRING)
   private MaritalStatus maritalStatus;

   private String email;
   private String phone;
   private String address;
   private String jobTitle;

   @Enumerated(EnumType.STRING)
   private Department department;

   private String service;



   @Enumerated(EnumType.STRING)
   private ContractType contractType;



   private Double baseSalary;// Monthly base salary (in local currency)
   private Double housingAllowance;// Monthly housing allowance/benefit

   private Integer evaluationScore;// Latest performance rating (e.g., 1-5 scale)



   private Integer createdBy; //ID of user who created this account (for audit)

   //private Boolean active;

   private String socialSecurityNumber;// N° de sécurité sociale/CSS number

   @ManyToOne// Many users can report to one project manager
   @JoinColumn(name = "assigned_project_manager_id")
   private User assignedProjectManager;// PM responsible for user's project work

   // Their direct supervisor
   @ManyToOne// Many users can report to one direct manager
   @JoinColumn(name = "direct_manager_id")
   private User directManager;



   // Many-to-Many with Role - User can have multiple roles, roles can have multiple users
   @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
   @JoinTable(
           name = "user_roles",
           joinColumns = @JoinColumn(name = "user_id"),
           inverseJoinColumns = @JoinColumn(name = "role_id")
   )
   @Builder.Default
   private List<Role> roles = new ArrayList<>();


   @Builder.Default
   private Integer childrenCount = 0;

   // As Employee: Team assignments where this user is assigned as team member
   @OneToMany(mappedBy = "employee")
   @Builder.Default
   private List<TeamAssignment> teamAssignments = new ArrayList<>();

   // As Project Manager: Projects managed by this user
   @OneToMany(mappedBy = "projectManager")
   @Builder.Default
   private List<Project> managedProjects = new ArrayList<>();

   // As Project Manager: Team assignments I created
   @OneToMany(mappedBy = "assigningManager")
   @Builder.Default
   private List<TeamAssignment> createdAssignments = new ArrayList<>();

   // CES CHAMPS POUR L'AUTHENTIFICATION
   @Column(unique = true)
   private String username; // EmployeeCode par défaut, modifiable par l'utilisateur

   @Column(nullable = false)
   private String passwordHash;


   @Builder.Default
   private boolean enabled = false; // false jusqu'à activation

   @Builder.Default
   private boolean accountNonExpired = true;

   @Builder.Default
   private boolean credentialsNonExpired = true;

   @Builder.Default
   private boolean accountNonLocked = true;

   @Builder.Default
   private boolean firstLogin = true; // Pour forcer le changement de mot de passe

   @Builder.Default
   public Boolean active = true;


   @Builder.Default
   private boolean registrationPending = false;

   @Column(columnDefinition = "TEXT")  // TEXT has unlimited length
   private String activationToken;
   private LocalDateTime activationTokenExpiry;


   @Column(columnDefinition = "TEXT")
   private String description;


   // Méthode pour Spring Security
   public Collection<? extends GrantedAuthority> getAuthorities() {
      return roles.stream()
              .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
              .collect(Collectors.toList());
   }
   @Column
   private String avatar; // URL of the user's profile photo




}