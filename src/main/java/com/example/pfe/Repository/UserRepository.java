package com.example.pfe.Repository;

import com.example.pfe.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmployeeCode(String employeeCode);
    Optional<User> findByEmail(String email);
    Optional<User> findByNationalId(String nationalId);


    /*List<User> findByDepartment(String department);
    List<User> findByActiveTrue();
    List<User> findByGender(String gender);
    List<User> findByMaritalStatus(String maritalStatus);
    List<User> findByContractType(String contractType);


    List<User> findByFirstNameContainingIgnoreCase(String firstName);
    List<User> findByLastNameContainingIgnoreCase(String lastName);

    boolean existsByEmail(String email);
    boolean existsByEmployeeCode(String employeeCode);*/
}