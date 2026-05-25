package com.mindlink.repository;

import com.mindlink.domain.User;
import com.mindlink.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(UserRole role);

    /** 클러스터 맵 — 실사용자 이름 부분 검색 */
    List<User> findByNameContainingIgnoreCase(String name);

    Optional<User> findFirstByName(String name);

    Optional<User> findFirstByNickname(String nickname);
}
