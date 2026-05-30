package com.mindlink.repository;

import com.mindlink.domain.User;
import com.mindlink.domain.UserAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAlertRepository extends JpaRepository<UserAlert, Long> {

    List<UserAlert> findByUserOrderByCreatedAtDesc(User user);

    List<UserAlert> findByUserAndReadFalseOrderByCreatedAtDesc(User user);

    long countByUserAndReadFalse(User user);

    Optional<UserAlert> findByIdAndUser(Long id, User user);

    void deleteByUser(User user);

    List<UserAlert> findByAlertTypeOrderByCreatedAtDesc(String alertType);

    long countByAlertTypeAndAdminConfirmedFalse(String alertType);
}
