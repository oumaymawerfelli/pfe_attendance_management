package com.example.pfe.Repository;

import com.example.pfe.entities.AttendanceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceConfigRepository extends JpaRepository<AttendanceConfig, Long> {

    Optional<AttendanceConfig> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);
}