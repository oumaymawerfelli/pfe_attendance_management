package com.example.pfe.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "team_assignments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TeamAssignment {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Integer id;

   @Column(name = "added_date")
   private LocalDate addedDate;

   private Boolean active;


   // The Employee assigned to work on the project
   @ManyToOne
   @JoinColumn(name = "employee_id")
   private User employee;

   // The Project this team is working on
   @ManyToOne
   @JoinColumn(name = "project_id")
   private Project project;

   // The Project Manager who selected this team member

   @ManyToOne
   @JoinColumn(name = "assigning_manager_id")  // Better name!
   private User assigningManager;
}