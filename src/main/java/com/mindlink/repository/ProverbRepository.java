package com.mindlink.repository;

import com.mindlink.domain.Proverb;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProverbRepository extends JpaRepository<Proverb, Long> {
    List<Proverb> findByPage(String page);
}
