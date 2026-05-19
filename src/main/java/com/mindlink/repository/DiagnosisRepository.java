package com.mindlink.repository;

import com.mindlink.domain.DiagnosisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiagnosisRepository extends JpaRepository<DiagnosisResult, Long> {
    List<DiagnosisResult> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<DiagnosisResult> findByTestTypeOrderByCreatedAtDesc(String testType);
}
