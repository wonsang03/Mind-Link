package com.mindlink.repository;

import com.mindlink.domain.AssessmentResult;
import com.mindlink.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, Long> {

    Optional<AssessmentResult> findTopByUserAndTypeKeyOrderByCompletedAtDesc(User user, String typeKey);

    boolean existsByUserAndHighRiskTrueAndCompletedAtAfter(User user, LocalDateTime since);
}
