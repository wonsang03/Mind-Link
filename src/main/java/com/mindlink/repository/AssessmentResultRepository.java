package com.mindlink.repository;

import com.mindlink.domain.AssessmentResult;
import com.mindlink.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, Long> {

    List<AssessmentResult> findByUserOrderByCreatedAtDesc(User user);

    Optional<AssessmentResult> findFirstByUserOrderByCreatedAtDesc(User user);

    void deleteByUser(User user);

    @Query("select distinct r.user from AssessmentResult r where r.highRisk = true and r.user is not null")
    List<User> findHighRiskUsers();
}
