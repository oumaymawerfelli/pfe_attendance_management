package com.example.pfe.entities;

import com.example.pfe.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Project {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Integer id;

   private String name;
   private String description;

   @Enumerated(EnumType.STRING)
   private ProjectStatus status;

   @Column(name = "assignment_date")
   private LocalDate assignmentDate;

   @ManyToOne
   @JoinColumn(name = "project_manager_id")
   private User projectManager;

   @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
   @Builder.Default
   private List<TeamAssignment> teamAssignments = new ArrayList<>();
}