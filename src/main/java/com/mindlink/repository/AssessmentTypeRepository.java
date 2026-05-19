package com.mindlink.repository;

import com.mindlink.domain.AssessmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentTypeRepository extends JpaRepository<AssessmentType, Long> {
    Optional<AssessmentType> findByTypeKey(String typeKey);
}
