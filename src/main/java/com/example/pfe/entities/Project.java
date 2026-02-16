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
   @Column(name = "IdProject")
   private Long id;

   private String code;
   private String name;
   private String description;

   @Enumerated(EnumType.STRING)
   private ProjectStatus status;

   private LocalDate startDate;
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