package com.mindlink.repository;

import com.mindlink.domain.AssessmentResult;
import com.mindlink.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, Long> {

    Optional<AssessmentResult> findTopByUserAndTypeKeyOrderByCompletedAtDesc(User user, String typeKey);

    boolean existsByUserAndHighRiskTrueAndCompletedAtAfter(User user, LocalDateTime since);

    /** 클러스터 3축(stress/depression/anxiety) 검사를 한 번이라도 완료한 사용자 */
    @org.springframework.data.jpa.repository.Query("""
            SELECT DISTINCT r.user FROM AssessmentResult r
            WHERE r.score IS NOT NULL
              AND r.typeKey IN ('stress', 'depression', 'anxiety')
            """)
    List<User> findDistinctUsersWithAxisAssessments();

    long countByHighRiskTrue();

    List<AssessmentResult> findByHighRiskTrueOrderByCompletedAtDesc();

    List<AssessmentResult> findTop5ByHighRiskTrueOrderByCompletedAtDesc();
}
