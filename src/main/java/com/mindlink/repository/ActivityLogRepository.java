package com.mindlink.repository;

import com.mindlink.domain.ActivityLog;
import com.mindlink.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    void deleteByUser(User user);

    List<ActivityLog> findByUserOrderByCreatedAtDesc(User user);

    List<ActivityLog> findByUserAndActivityKeyOrderByCreatedAtDesc(User user, String activityKey);

    long countByUser(User user);

    long countByUserAndCreatedAtAfter(User user, LocalDateTime since);
}
