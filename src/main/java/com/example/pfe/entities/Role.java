package com.example.pfe.entities;



import com.example.pfe.enums.RoleName;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "roleEmployee")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   @Column(name = "idRole")
   private Integer id;


   @Enumerated(EnumType.STRING)
   @Column(unique = true)
   private RoleName name;

   private String description;
}
