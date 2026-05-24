package com.mindlink.care;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CareReportRepository extends JpaRepository<CareReport, Long> {

    List<CareReport> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<CareReport> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime threshold);
}
