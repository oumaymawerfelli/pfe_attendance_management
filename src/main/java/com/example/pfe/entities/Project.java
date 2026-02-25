package com.example.pfe.entities;

import com.example.pfe.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   @Column(name = "id")  // Changed to match database column name
   private Long id;

   @Column(name = "code")
   private String code;

   @Column(name = "name")
   private String name;

   @Column(name = "description")
   private String description;

   @Enumerated(EnumType.STRING)
   @Column(name = "status")
   private ProjectStatus status;

   @Column(name = "start_date")  // Changed to match database column name
   private LocalDate startDate;

   @Column(name = "end_date")    // Changed to match database column name
   private LocalDate endDate;

   @Column(name = "assignment_date")
   private LocalDateTime assignmentDate;

   @Column(name = "created_at")
   private LocalDateTime createdAt;

   @Column(name = "updated_at")
   private LocalDateTime updatedAt;

   @ManyToOne
   @JoinColumn(name = "project_manager_id")
   private User projectManager;

   @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
   @Builder.Default
   private List<TeamAssignment> teamAssignments = new ArrayList<>();
}