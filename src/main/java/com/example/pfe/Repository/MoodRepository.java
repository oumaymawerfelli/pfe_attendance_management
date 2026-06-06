package com.example.pfe.Repository;

import com.example.pfe.entities.MoodEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MoodRepository extends JpaRepository<MoodEntry, Long> {

    boolean existsByUserIdAndDate(Long userId, LocalDate date);

    Optional<MoodEntry> findByUserIdAndDate(Long userId, LocalDate date);

    List<MoodEntry> findAllByDate(LocalDate date);

    List<MoodEntry> findAllByDateBetween(LocalDate from, LocalDate to);

    @Query("SELECT COUNT(m) FROM MoodEntry m WHERE m.date = :date")
    long countByDate(@Param("date") LocalDate date);

    @Query("SELECT AVG(m.score) FROM MoodEntry m WHERE m.date = :date")
    Double avgScoreByDate(@Param("date") LocalDate date);

    @Query("SELECT AVG(m.score) FROM MoodEntry m WHERE m.date = :date AND m.department = :dept")
    Double avgScoreByDateAndDept(@Param("date") LocalDate date, @Param("dept") String dept);
}