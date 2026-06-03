package com.mindlink.chatcluster;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAssessmentProfileRepository extends JpaRepository<UserAssessmentProfile, Long> {

    Optional<UserAssessmentProfile> findByUserId(Long userId);

    List<UserAssessmentProfile> findAllByIsSynthetic(Integer isSynthetic);

    long countByIsSynthetic(Integer isSynthetic);

    /**
     * 실사용자 검색 — 이름(users.name)·라벨(persona_label)·이메일 부분 일치.
     * pattern 은 서비스에서 {@code %검색어%} 형태로 넘긴다.
     */
    @Query("""
            SELECT p FROM UserAssessmentProfile p
            WHERE p.userId IS NOT NULL
              AND p.isSynthetic = 0
              AND (
                   LOWER(p.personaLabel) LIKE :pattern
                OR p.userId IN (
                    SELECT u.id FROM User u
                    WHERE LOWER(u.name) LIKE :pattern OR LOWER(u.email) LIKE :pattern
                )
              )
            """)
    List<UserAssessmentProfile> searchRealUsers(@Param("pattern") String pattern);

    @Query("""
            SELECT p.clusterId, COUNT(p) FROM UserAssessmentProfile p
            WHERE p.userId IS NOT NULL AND p.isSynthetic = 0
            GROUP BY p.clusterId
            """)
    List<Object[]> countRealUsersByCluster();

    /** E-1 — 실사용자만 군집별 인원·평균 norm (페르소나 제외). [clusterId, cnt, avgS, avgD, avgA] */
    @Query("""
            SELECT p.clusterId, COUNT(p),
                   AVG(p.stressNorm), AVG(p.depressionNorm), AVG(p.anxietyNorm)
            FROM UserAssessmentProfile p
            WHERE p.userId IS NOT NULL AND p.isSynthetic = 0 AND p.clusterId IS NOT NULL
            GROUP BY p.clusterId
            ORDER BY p.clusterId
            """)
    List<Object[]> realClusterStats();

    /** C-1 — 같은 군집의 실사용자 user_id 목록 (페르소나 제외). */
    @Query("""
            SELECT p.userId FROM UserAssessmentProfile p
            WHERE p.clusterId = :clusterId AND p.userId IS NOT NULL AND p.isSynthetic = 0
            """)
    List<Long> findRealUserIdsByCluster(@Param("clusterId") Integer clusterId);
}
