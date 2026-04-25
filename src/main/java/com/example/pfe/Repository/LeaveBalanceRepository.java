package com.example.pfe.Repository;

import com.example.pfe.entities.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    // Find balance for a specific user and year
    Optional<LeaveBalance> findByUserIdAndYear(Long userId, Integer year);

    // Check if balance already exists for this user/year
    boolean existsByUserIdAndYear(Long userId, Integer year);
}